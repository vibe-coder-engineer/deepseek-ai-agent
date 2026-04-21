package ru.sibgatulinanton;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import ru.sibgatulinanton.deepseek.BrowserDriverManager;
import ru.sibgatulinanton.deepseek.DeepSeekChatPage;
import ru.sibgatulinanton.lang.Language;
import ru.sibgatulinanton.os.OSType;
import ru.sibgatulinanton.os.OSUtils;
import ru.sibgatulinanton.os.cmd.CompleteCmd;
import ru.sibgatulinanton.prompts.PromptLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public class App {

    private static final String CONTINUE_PROMPT = "продолжай";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Path VIBE_DIR = Paths.get(".vibecoder");
    private static final Path SESSIONS_DIR = VIBE_DIR.resolve("sessions");
    private static final Path CURRENT_SESSION_FILE = VIBE_DIR.resolve("current-session.txt");

    public static void main(String[] args) {
        log("INFO", "DeepSeek agent started in console mode");

        String currentDir = System.getProperty("user.dir");
        OSType osType = OSUtils.getOperatingSystemType();

        ensureStorage();

        PromptLoader promptLoader = new PromptLoader();
        BrowserDriverManager manager = new BrowserDriverManager();

        try (Scanner scanner = new Scanner(System.in)) {
            manager.openDeepSeek();
            DeepSeekChatPage deepSeekPage = new DeepSeekChatPage(manager);

            ensureLoggedInOrWait(scanner, deepSeekPage, "startup");

            manager.driverWait(30);

            SessionSelection selection = chooseSession(scanner);
            String prompt;
            String task;

            if (selection.resume) {
                manager.openUrl(selection.session.url);
                log("INFO", "Resumed dialog: " + selection.session.url);
                ensureLoggedInOrWait(scanner, deepSeekPage, "resume");
                task = selection.session.task;
                prompt = askLine(scanner, "Enter message for resumed dialog (Enter = 'продолжай'): ");
                if (prompt.trim().isEmpty()) {
                    prompt = CONTINUE_PROMPT;
                }
                saveSession(selection.session.chatId, selection.session.url, task, true);
            } else {
                task = askLine(scanner, "Enter task for DeepSeek: ");
                if (task.trim().isEmpty()) {
                    task = "Write hello world in Java";
                }

                String firstPromptTemplate = promptLoader.getPrompt(Language.RU, "first_prompt");
                if (firstPromptTemplate == null) {
                    throw new IllegalStateException("Prompt template resources/prompts/ru/first_prompt.txt not found");
                }

                prompt = firstPromptTemplate
                        .replace("{TASK}", task)
                        .replace("{OS}", osType.name())
                        .replace("{WORKSPACE}", currentDir)
                        .replace("{CMD}", OSType.WINDOWS.equals(osType) ? "PowerShell" : "bash");
            }

            boolean isEnd = false;
            boolean promptExists;
            String activeChatId = selection.resume ? selection.session.chatId : null;
            int consecutiveStepFailures = 0;

            while (!isEnd) {
                promptExists = false;
                ensureLoggedInOrWait(scanner, deepSeekPage, "before send");

                logBlock("PROMPT_TO_AI", prompt);
                String rawResponse;
                try {
                    rawResponse = deepSeekPage.askDeepSeek(prompt);
                } catch (RuntimeException ex) {
                    consecutiveStepFailures++;
                    String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                    if (msg.contains("авториз")) {
                        log("WARN", "DeepSeek session is not authorized. Re-login in browser and press Enter.");
                        scanner.nextLine();
                        ensureLoggedInOrWait(scanner, deepSeekPage, "retry after unauthorized");
                        continue;
                    }

                    log("WARN", "Step failed: " + ex.getMessage());
                    if (consecutiveStepFailures > 5) {
                        throw ex;
                    }

                    recoverDialogState(manager, deepSeekPage, selection, activeChatId, scanner);
                    log("INFO", "Retrying last step with same prompt");
                    continue;
                }
                consecutiveStepFailures = 0;
                logBlock("AI_RAW_RESPONSE", rawResponse);

                String currentChatId = deepSeekPage.getChatIdFromCurrentUrl();
                String currentUrl = deepSeekPage.getCurrentUrl();
                if (currentChatId != null && !currentChatId.trim().isEmpty()) {
                    activeChatId = currentChatId;
                    saveSession(activeChatId, currentUrl, task, selection.resume);
                    log("INFO", "Session saved: chatId=" + activeChatId);
                }

                String response = cleanResponse(rawResponse);
                logBlock("AI_CLEAN_RESPONSE", response);

                JSONObject jsonObject;
                try {
                    jsonObject = new JSONObject(response);
                } catch (Exception ex) {
                    log("ERROR", "Failed to parse AI response as JSON: " + ex.getMessage());
                    consecutiveStepFailures++;
                    if (consecutiveStepFailures > 5) {
                        break;
                    }
                    log("WARN", "Retrying last step due to JSON parse error");
                    continue;
                }

                JSONArray operations = jsonObject.optJSONArray("operations");
                if (operations == null) {
                    log("ERROR", "AI response doesn't contain operations[]");
                    consecutiveStepFailures++;
                    if (consecutiveStepFailures > 5) {
                        break;
                    }
                    log("WARN", "Retrying last step because operations[] is missing");
                    continue;
                }

                for (int i = 0; i < operations.length(); i++) {
                    JSONObject operation = operations.getJSONObject(i);
                    String type = operation.optString("type", "");
                    String data = operation.optString("data", "");
                    String content = operation.has("content") && !operation.isNull("content")
                            ? StringEscapeUtils.escapeEcmaScript(operation.getString("content"))
                            : null;

                    if ("END".equals(type)) {
                        isEnd = true;
                        logBlock("AI_END", content == null ? "<empty>" : content);
                    } else if ("CMD".equals(type) || "CMD_WAIT".equals(type)) {
                        if (content != null) {
                            data = data.replace("%s", content);
                        }

                        logBlock("LOCAL_COMMAND", data);
                        String cmdResult = OSType.WINDOWS.equals(osType)
                                ? CompleteCmd.executePowerShell(data + "\n")
                                : CompleteCmd.executeCommand(data);
                        logBlock("LOCAL_COMMAND_RESULT", cmdResult);

                        if ("CMD_WAIT".equals(type)) {
                            prompt = cmdResult == null || cmdResult.trim().isEmpty() ? CONTINUE_PROMPT : cmdResult;
                            promptExists = true;
                        }
                    } else if ("TEXT".equals(type)) {
                        logBlock("AI_TEXT", data);
                    } else {
                        log("WARN", "Unknown operation type: " + type);
                    }
                }

                if (!promptExists && !isEnd) {
                    prompt = CONTINUE_PROMPT;
                }
            }

            log("INFO", "Dialog finished. Press Enter to exit.");
            scanner.nextLine();
        } finally {
            try {
                manager.quit();
            } catch (Exception ignored) {
            }
        }
    }

    private static String cleanResponse(String rawResponse) {
        if (rawResponse == null) {
            return "";
        }

        return rawResponse
                .replace("json", "")
                .replace("Копировать", "")
                .replace("Скачать", "")
                .trim();
    }

    private static void recoverDialogState(
            BrowserDriverManager manager,
            DeepSeekChatPage deepSeekPage,
            SessionSelection selection,
            String activeChatId,
            Scanner scanner
    ) {
        try {
            String targetUrl;
            if (activeChatId != null && !activeChatId.trim().isEmpty()) {
                targetUrl = BrowserDriverManager.CHAT_DEEPSEEK_LINK + "/a/chat/s/" + activeChatId;
            } else if (selection.resume && selection.session != null && selection.session.url != null && !selection.session.url.trim().isEmpty()) {
                targetUrl = selection.session.url;
            } else {
                targetUrl = BrowserDriverManager.CHAT_DEEPSEEK_LINK;
            }

            log("WARN", "Recovering dialog state. Open URL: " + targetUrl);
            manager.openUrl(targetUrl);
            ensureLoggedInOrWait(scanner, deepSeekPage, "recovery");
        } catch (Exception e) {
            log("WARN", "Recovery failed: " + e.getMessage());
        }
    }

    private static SessionSelection chooseSession(Scanner scanner) {
        List<SessionInfo> sessions = listSessions();

        log("INFO", "Choose mode:");
        log("INFO", "1 - start NEW dialog");
        log("INFO", "2 - RESUME existing dialog");

        String mode = askLine(scanner, "Enter mode (1/2): ");
        if (!"2".equals(mode) || sessions.isEmpty()) {
            if ("2".equals(mode)) {
                log("WARN", "No saved sessions found. Starting new dialog.");
            }
            return SessionSelection.newSession();
        }

        log("INFO", "Saved sessions:");
        for (int i = 0; i < sessions.size(); i++) {
            SessionInfo s = sessions.get(i);
            log("INFO", (i + 1) + ") " + s.chatId + " | lastUsed=" + s.lastUsedAt + " | task=" + s.task);
        }

        String selected = askLine(scanner, "Enter session number: ");
        try {
            int index = Integer.parseInt(selected) - 1;
            if (index >= 0 && index < sessions.size()) {
                return SessionSelection.resume(sessions.get(index));
            }
        } catch (Exception ignored) {
        }

        log("WARN", "Invalid session number. Starting new dialog.");
        return SessionSelection.newSession();
    }

    private static List<SessionInfo> listSessions() {
        List<SessionInfo> sessions = new ArrayList<SessionInfo>();
        if (!Files.exists(SESSIONS_DIR)) {
            return sessions;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(SESSIONS_DIR, "*.json")) {
            for (Path file : stream) {
                try {
                    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(content);
                    SessionInfo info = new SessionInfo();
                    info.chatId = json.optString("chatId", "");
                    info.url = json.optString("url", "");
                    info.task = json.optString("task", "");
                    info.lastUsedAt = json.optString("lastUsedAt", "");
                    if (!info.chatId.isEmpty() && !info.url.isEmpty()) {
                        sessions.add(info);
                    }
                } catch (Exception e) {
                    log("WARN", "Failed to read session file: " + file + ". " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log("WARN", "Failed to list sessions: " + e.getMessage());
        }

        sessions.sort(new Comparator<SessionInfo>() {
            @Override
            public int compare(SessionInfo a, SessionInfo b) {
                return b.lastUsedAt.compareTo(a.lastUsedAt);
            }
        });

        return sessions;
    }

    private static void ensureStorage() {
        try {
            Files.createDirectories(SESSIONS_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create .vibecoder storage", e);
        }
    }

    private static void saveSession(String chatId, String url, String task, boolean resumed) {
        if (chatId == null || chatId.trim().isEmpty() || url == null || url.trim().isEmpty()) {
            return;
        }

        String now = LocalDateTime.now().format(TS);
        Path sessionFile = SESSIONS_DIR.resolve(chatId + ".json");

        JSONObject json = new JSONObject();
        if (Files.exists(sessionFile)) {
            try {
                String existing = new String(Files.readAllBytes(sessionFile), StandardCharsets.UTF_8);
                json = new JSONObject(existing);
            } catch (Exception ignored) {
            }
        }

        if (!json.has("createdAt")) {
            json.put("createdAt", now);
        }

        json.put("chatId", chatId);
        json.put("url", url);
        json.put("task", task == null ? "" : task);
        json.put("lastUsedAt", now);
        json.put("resumed", resumed);

        try {
            Files.write(sessionFile, json.toString(2).getBytes(StandardCharsets.UTF_8));
            Files.write(CURRENT_SESSION_FILE, chatId.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log("WARN", "Failed to save session: " + e.getMessage());
        }
    }

    private static String askLine(Scanner scanner, String prompt) {
        System.out.print(prompt);
        try {
            return scanner.nextLine();
        } catch (Exception e) {
            return "";
        }
    }

    private static void ensureLoggedInOrWait(Scanner scanner, DeepSeekChatPage deepSeekPage, String stage) {
        while (!deepSeekPage.isUserLoggedIn()) {
            log("WARN", "User is NOT authenticated in DeepSeek (" + stage + "). Authenticate in browser and press Enter.");
            scanner.nextLine();

            for (int i = 0; i < 20; i++) {
                if (deepSeekPage.isUserLoggedIn()) {
                    break;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }
        log("INFO", "User is authenticated in DeepSeek (" + stage + ")");
    }

    private static void log(String level, String message) {
        String ts = LocalDateTime.now().format(TS);
        System.out.println("[" + ts + "] [" + level + "] " + message);
    }

    private static void logBlock(String title, String value) {
        String ts = LocalDateTime.now().format(TS);
        System.out.println("[" + ts + "] [" + title + "] >>>");
        System.out.println(value == null ? "<null>" : value);
        System.out.println("[" + ts + "] [" + title + "] <<<");
    }

    private static class SessionInfo {
        String chatId;
        String url;
        String task;
        String lastUsedAt;
    }

    private static class SessionSelection {
        final boolean resume;
        final SessionInfo session;

        private SessionSelection(boolean resume, SessionInfo session) {
            this.resume = resume;
            this.session = session;
        }

        static SessionSelection newSession() {
            return new SessionSelection(false, null);
        }

        static SessionSelection resume(SessionInfo session) {
            return new SessionSelection(true, session);
        }
    }
}
