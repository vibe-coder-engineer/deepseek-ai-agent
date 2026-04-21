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
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public class App {

    private static final String CONTINUE_PROMPT = "continue";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Path VIBE_DIR = Paths.get(System.getProperty("user.home"), ".vibecoder");
    private static final Path SESSIONS_DIR = VIBE_DIR.resolve("sessions");
    private static final Path CURRENT_SESSION_FILE = VIBE_DIR.resolve("current-session.txt");

    public static void main(String[] args) {
        boolean headless = hasArg(args, "--headless");
        String execPromptArg = getArgValue(args, "--exec");
        String threadArg = getArgValue(args, "--thread");
        boolean execMode = execPromptArg != null && !execPromptArg.trim().isEmpty();

        log("INFO", "DeepSeek agent started in console mode");
        log("INFO", "Browser mode: " + (headless ? "headless" : "headed"));
        if (execMode) {
            log("INFO", "Exec mode enabled");
        }

        String currentDir = System.getProperty("user.dir");
        OSType osType = OSUtils.getOperatingSystemType();

        ensureStorage();

        PromptLoader promptLoader = new PromptLoader();
        BrowserDriverManager manager = new BrowserDriverManager(headless);

        try (Scanner scanner = new Scanner(System.in)) {
            DeepSeekChatPage deepSeekPage = new DeepSeekChatPage(manager);

            if (execMode) {
                if (threadArg != null && !threadArg.trim().isEmpty()) {
                    manager.openUrl(toThreadUrl(threadArg.trim()));
                    ensureLoggedInOrWait(scanner, deepSeekPage, "exec-thread");
                } else {
                    manager.openDeepSeek();
                    ensureLoggedInOrWait(scanner, deepSeekPage, "exec");
                }

                manager.driverWait(30);
                runDialogUntilEnd(scanner, deepSeekPage, osType, execPromptArg, execPromptArg, threadArg != null && !threadArg.trim().isEmpty());
                log("INFO", "Exec mode completed. Exit.");
                System.exit(0);
            }

            manager.openDeepSeek();
            ensureLoggedInOrWait(scanner, deepSeekPage, "startup");
            manager.driverWait(30);

            boolean appRunning = true;
            SessionSelection selection = null;
            String task = null;
            String prompt = null;
            String activeChatId = null;

            while (appRunning) {
                if (selection == null) {
                    selection = chooseSession(scanner, manager, deepSeekPage);
                    if (selection == null) {
                        break;
                    }

                    if (selection.resume) {
                        manager.openUrl(selection.session.url);
                        ensureLoggedInOrWait(scanner, deepSeekPage, "resume");

                        task = selection.session.task;
                        activeChatId = selection.session.chatId;

                        prompt = askLine(scanner, "Enter message for resumed dialog (Enter = 'continue'): ");
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
                }
                DialogRunResult runResult = runDialogUntilEnd(scanner, deepSeekPage, osType, prompt, task, selection.resume);
                if (runResult.activeChatId != null && !runResult.activeChatId.trim().isEmpty()) {
                    activeChatId = runResult.activeChatId;
                }

                String action = askPostEndAction(scanner);
                if ("1".equals(action)) {
                    if (activeChatId != null && !activeChatId.trim().isEmpty()) {
                        manager.openUrl(BrowserDriverManager.CHAT_DEEPSEEK_LINK + "/a/chat/s/" + activeChatId);
                        ensureLoggedInOrWait(scanner, deepSeekPage, "post-end continue");
                    }

                    prompt = askLine(scanner, "Enter message for this dialog (Enter = 'continue'): ");
                    if (prompt.trim().isEmpty()) {
                        prompt = CONTINUE_PROMPT;
                    }
                    continue;
                }

                if ("2".equals(action)) {
                    selection = null;
                    task = null;
                    prompt = null;
                    activeChatId = null;
                    manager.openDeepSeek();
                    ensureLoggedInOrWait(scanner, deepSeekPage, "new dialog");
                    continue;
                }

                if ("3".equals(action)) {
                    deleteDialogFlow(scanner, activeChatId);
                    selection = null;
                    task = null;
                    prompt = null;
                    activeChatId = null;
                    manager.openDeepSeek();
                    ensureLoggedInOrWait(scanner, deepSeekPage, "after delete");
                    continue;
                }

                appRunning = false;
            }

            log("INFO", "App finished. Press Enter to exit.");
            scanner.nextLine();
        } finally {
            try {
                manager.quit();
            } catch (Exception ignored) {
            }
        }
    }
    private static DialogRunResult runDialogUntilEnd(Scanner scanner,
                                                      DeepSeekChatPage deepSeekPage,
                                                      OSType osType,
                                                      String initialPrompt,
                                                      String task,
                                                      boolean resumed) {
        String prompt = initialPrompt;
        String activeChatId = null;
        boolean isEnd = false;
        int consecutiveStepFailures = 0;

        while (!isEnd) {
            boolean promptExists = false;
            ensureLoggedInOrWait(scanner, deepSeekPage, "before send");

            logBlock("PROMPT_TO_AI", prompt);

            String rawResponse;
            try {
                rawResponse = deepSeekPage.askDeepSeek(prompt);
            } catch (RuntimeException ex) {
                consecutiveStepFailures++;
                String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                if (msg.contains("авториз") || msg.contains("not authorized")) {
                    log("WARN", "DeepSeek session is not authorized. Re-login in browser and press Enter.");
                    scanner.nextLine();
                    ensureLoggedInOrWait(scanner, deepSeekPage, "retry after unauthorized");
                } else {
                    log("WARN", "Step failed: " + ex.getMessage());
                }

                if (consecutiveStepFailures > 20) {
                    log("WARN", "Too many failures. Reset counter and keep retrying same step.");
                    consecutiveStepFailures = 0;
                }
                continue;
            }
            consecutiveStepFailures = 0;

            logBlock("AI_RAW_RESPONSE", rawResponse);

            String currentChatId = deepSeekPage.getChatIdFromCurrentUrl();
            String currentUrl = deepSeekPage.getCurrentUrl();
            if (currentChatId != null && !currentChatId.trim().isEmpty()) {
                activeChatId = currentChatId;
                saveSession(activeChatId, currentUrl, task, resumed);
                log("INFO", "Session saved: chatId=" + activeChatId);
            }

            String response = cleanResponse(rawResponse);
            logBlock("AI_CLEAN_RESPONSE", response);

            JSONObject jsonObject = extractJsonObject(response);
            if (jsonObject == null) {
                log("ERROR", "Failed to parse AI response as JSON payload");
                continue;
            }

            JSONArray operations = jsonObject.optJSONArray("operations");
            if (operations == null) {
                if (jsonObject.has("type")) {
                    operations = new JSONArray();
                    JSONObject singleOperation = new JSONObject();
                    singleOperation.put("type", jsonObject.optString("type", ""));
                    singleOperation.put("data", jsonObject.optString("data", ""));
                    if (jsonObject.has("content") && !jsonObject.isNull("content")) {
                        singleOperation.put("content", jsonObject.get("content"));
                    }
                    operations.put(singleOperation);
                } else {
                    log("ERROR", "AI response doesn't contain operations[] or top-level type");
                    continue;
                }
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
                    break;
                }

                if ("CMD".equals(type) || "CMD_WAIT".equals(type)) {
                    if (content != null) {
                        data = data.replace("%s", content);
                    }

                    logBlock("LOCAL_COMMAND", data);
                    String cmdResult = runLocalCommandWithCapture(data, osType);
                    logBlock("LOCAL_COMMAND_RESULT", cmdResult);

                    if ("CMD_WAIT".equals(type) || isCommandFailed(cmdResult)) {
                        prompt = (cmdResult == null || cmdResult.trim().isEmpty()) ? CONTINUE_PROMPT : cmdResult;
                        promptExists = true;
                    }
                    continue;
                }

                if ("TEXT".equals(type)) {
                    continue;
                }

                log("WARN", "Unknown operation type: " + type);
            }

            if (!promptExists && !isEnd) {
                prompt = CONTINUE_PROMPT;
            }
        }

        return new DialogRunResult(activeChatId);
    }
    private static String askPostEndAction(Scanner scanner) {
        log("INFO", "Dialog finished. Choose action:");
        log("INFO", "1 - continue this dialog");
        log("INFO", "2 - start new dialog");
        log("INFO", "3 - delete dialog(s)");
        log("INFO", "4 - exit");

        String action = askLine(scanner, "Enter action (1/2/3/4): ").trim();
        if (!"1".equals(action) && !"2".equals(action) && !"3".equals(action) && !"4".equals(action)) {
            return "4";
        }
        return action;
    }

    private static String runLocalCommandWithCapture(String command, OSType osType) {
        try {
            return OSType.WINDOWS.equals(osType)
                    ? CompleteCmd.executePowerShell(command + "\n")
                    : CompleteCmd.executeCommand(command);
        } catch (Exception ex) {
            String error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            return "COMMAND_ERROR\nFAILED_COMMAND:\n" + command + "\nERROR:\n" + error;
        }
    }

    private static boolean isCommandFailed(String commandResult) {
        if (commandResult == null) {
            return false;
        }

        String lower = commandResult.toLowerCase();
        return lower.contains("command_error")
                || lower.contains("exception")
                || lower.contains("РѕС€РёР±РєР°")
                || lower.contains("error");
    }

    private static String cleanResponse(String rawResponse) {
        if (rawResponse == null) {
            return "";
        }

        return rawResponse
                .replace("```json", "")
                .replace("```", "")
                .replaceAll("(?im)^\\s*json\\s*$", "")
                .replaceAll("(?im)^\\s*copy\\s*$", "")
                .replaceAll("(?im)^\\s*download\\s*$", "")
                .replaceAll("(?im)^\\s*РєРѕРїРёСЂРѕРІР°С‚СЊ\\s*$", "")
                .replaceAll("(?im)^\\s*СЃРєР°С‡Р°С‚СЊ\\s*$", "")
                .trim();
    }

    private static JSONObject extractJsonObject(String text) {
        if (text == null) {
            return null;
        }

        String trimmed = text.trim();
        try {
            return new JSONObject(trimmed);
        } catch (Exception ignored) {
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return null;
        }

        String candidate = trimmed.substring(firstBrace, lastBrace + 1).trim();
        try {
            return new JSONObject(candidate);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SessionSelection chooseSession(Scanner scanner, BrowserDriverManager manager, DeepSeekChatPage deepSeekPage) {
        while (true) {
            List<SessionInfo> sessions = listSessions();

            log("INFO", "Choose mode:");
            log("INFO", "1 - start NEW dialog");
            log("INFO", "2 - RESUME existing dialog");
            log("INFO", "3 - DELETE saved dialog");
            log("INFO", "4 - EXIT app");

            String mode = askLine(scanner, "Enter mode (1/2/3/4): ").trim();
            if ("1".equals(mode)) {
                return SessionSelection.newSession();
            }

            if ("2".equals(mode)) {
                if (sessions.isEmpty()) {
                    log("WARN", "No saved sessions found.");
                    continue;
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

                log("WARN", "Invalid session number.");
                continue;
            }

            if ("3".equals(mode)) {
                deleteDialogFlow(scanner, null);
                manager.openDeepSeek();
                ensureLoggedInOrWait(scanner, deepSeekPage, "after delete");
                continue;
            }

            if ("4".equals(mode)) {
                return null;
            }

            log("WARN", "Unknown mode.");
        }
    }

    private static void deleteDialogFlow(Scanner scanner, String preferredChatId) {
        List<SessionInfo> sessions = listSessions();
        if (sessions.isEmpty()) {
            log("WARN", "No saved dialogs to delete.");
            return;
        }

        log("INFO", "Delete dialog mode:");
        for (int i = 0; i < sessions.size(); i++) {
            SessionInfo s = sessions.get(i);
            String mark = (preferredChatId != null && preferredChatId.equals(s.chatId)) ? " *current" : "";
            log("INFO", (i + 1) + ") " + s.chatId + mark + " | lastUsed=" + s.lastUsedAt + " | task=" + s.task);
        }

        String selected = askLine(scanner, "Enter number to delete (or empty to cancel): ").trim();
        if (selected.isEmpty()) {
            log("INFO", "Delete canceled.");
            return;
        }

        try {
            int index = Integer.parseInt(selected) - 1;
            if (index < 0 || index >= sessions.size()) {
                log("WARN", "Invalid selection.");
                return;
            }

            SessionInfo target = sessions.get(index);
            Path file = SESSIONS_DIR.resolve(target.chatId + ".json");
            if (Files.exists(file)) {
                Files.delete(file);
                log("INFO", "Dialog deleted: " + target.chatId);
            } else {
                log("WARN", "Dialog file not found: " + file);
            }

            try {
                if (Files.exists(CURRENT_SESSION_FILE)) {
                    String current = new String(Files.readAllBytes(CURRENT_SESSION_FILE), StandardCharsets.UTF_8).trim();
                    if (target.chatId.equals(current)) {
                        Files.delete(CURRENT_SESSION_FILE);
                    }
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            log("WARN", "Delete failed: " + e.getMessage());
        }
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
            Path legacyVibeDir = Paths.get(System.getProperty("user.dir"), ".vibecoder");
            if (Files.exists(legacyVibeDir) && !legacyVibeDir.equals(VIBE_DIR) && !Files.exists(VIBE_DIR)) {
                Files.createDirectories(VIBE_DIR.getParent());
                Files.move(legacyVibeDir, VIBE_DIR, StandardCopyOption.REPLACE_EXISTING);
                log("INFO", "Storage migrated: " + legacyVibeDir + " -> " + VIBE_DIR);
            }
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

    private static boolean hasArg(String[] args, String targetArg) {
        if (args == null || targetArg == null || targetArg.trim().isEmpty()) {
            return false;
        }

        for (String arg : args) {
            if (targetArg.equalsIgnoreCase(arg)) {
                return true;
            }
        }

        return false;
    }
    private static String getArgValue(String[] args, String targetArg) {
        if (args == null || targetArg == null || targetArg.trim().isEmpty()) {
            return null;
        }

        String prefix = targetArg + "=";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            }

            if (targetArg.equalsIgnoreCase(arg)) {
                if (i + 1 < args.length) {
                    return args[i + 1];
                }
                return null;
            }

            if (arg.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return arg.substring(prefix.length());
            }
        }

        return null;
    }

    private static String toThreadUrl(String threadArg) {
        if (threadArg == null || threadArg.trim().isEmpty()) {
            return BrowserDriverManager.CHAT_DEEPSEEK_LINK;
        }

        String trimmed = threadArg.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        return BrowserDriverManager.CHAT_DEEPSEEK_LINK + "/a/chat/s/" + trimmed;
    }

    private static class DialogRunResult {
        final String activeChatId;

        private DialogRunResult(String activeChatId) {
            this.activeChatId = activeChatId;
        }
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



