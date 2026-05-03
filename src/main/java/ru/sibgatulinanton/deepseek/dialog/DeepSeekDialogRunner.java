package ru.sibgatulinanton.deepseek.dialog;

import org.apache.commons.lang3.StringEscapeUtils;
import ru.sibgatulinanton.AppConstants;
import ru.sibgatulinanton.cli.ConsoleInput;
import ru.sibgatulinanton.deepseek.DeepSeekAuthGuard;
import ru.sibgatulinanton.deepseek.DeepSeekChatPage;
import ru.sibgatulinanton.deepseek.storage.SessionController;
import ru.sibgatulinanton.files.FileOperationService;
import ru.sibgatulinanton.http.HttpRequestService;
import ru.sibgatulinanton.logging.ConsoleLogger;
import ru.sibgatulinanton.os.cmd.CommandFailureDetector;
import ru.sibgatulinanton.os.cmd.LocalCommandService;
import ru.sibgatulinanton.rag.RagOperationService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeepSeekDialogRunner {

    private static final int MAX_CONSECUTIVE_STEP_FAILURES = 20;

    private final ConsoleInput input;
    private final ConsoleLogger logger;
    private final DeepSeekAuthGuard authGuard;
    private final SessionController sessions;
    private final AiResponseParser responseParser;
    private final LocalCommandService commandService;
    private final CommandFailureDetector failureDetector;
    private final FileOperationService fileOperationService;
    private final RagOperationService ragOperationService;
    private final HttpRequestService httpRequestService;
    private final Map<String, ParallelChat> parallelChats = new LinkedHashMap<String, ParallelChat>();

    public DeepSeekDialogRunner(ConsoleInput input,
                                ConsoleLogger logger,
                                DeepSeekAuthGuard authGuard,
                                SessionController sessions,
                                AiResponseParser responseParser,
                                LocalCommandService commandService,
                                CommandFailureDetector failureDetector,
                                FileOperationService fileOperationService,
                                RagOperationService ragOperationService,
                                HttpRequestService httpRequestService) {
        this.input = input;
        this.logger = logger;
        this.authGuard = authGuard;
        this.sessions = sessions;
        this.responseParser = responseParser;
        this.commandService = commandService;
        this.failureDetector = failureDetector;
        this.fileOperationService = fileOperationService;
        this.ragOperationService = ragOperationService;
        this.httpRequestService = httpRequestService;
    }

    public DialogRunResult runUntilEnd(DeepSeekChatPage deepSeekPage, String initialPrompt, String task, boolean resumed) {
        String prompt = initialPrompt;
        String activeChatId = null;
        boolean isEnd = false;
        int consecutiveStepFailures = 0;

        while (!isEnd) {
            boolean promptExists = false;
            authGuard.ensureLoggedInOrWait(deepSeekPage, "before send");
            logger.block("PROMPT_TO_AI", prompt);

            String rawResponse;
            try {
                rawResponse = deepSeekPage.askDeepSeek(prompt);
            } catch (RuntimeException ex) {
                consecutiveStepFailures = handleStepFailure(deepSeekPage, ex, consecutiveStepFailures);
                continue;
            }
            consecutiveStepFailures = 0;

            logger.block("AI_RAW_RESPONSE", rawResponse);
            activeChatId = saveActiveSession(deepSeekPage, task, resumed, activeChatId);

            String response = responseParser.cleanResponse(rawResponse);
            logger.block("AI_CLEAN_RESPONSE", response);

            List<AiOperation> operations = responseParser.parseOperations(response);
            if (operations == null) {
                logger.error("AI response doesn't contain operations[] or top-level type");
                continue;
            }

            OperationResult result = executeOperations(deepSeekPage, operations);
            isEnd = result.isEnd();
            promptExists = result.hasNextPrompt();
            if (promptExists) {
                prompt = result.getNextPrompt();
            }

            if (!promptExists && !isEnd) {
                prompt = AppConstants.CONTINUE_PROMPT;
            }
        }

        return new DialogRunResult(activeChatId);
    }

    private int handleStepFailure(DeepSeekChatPage deepSeekPage, RuntimeException ex, int consecutiveStepFailures) {
        int failures = consecutiveStepFailures + 1;
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (msg.contains("авториз") || msg.contains("not authorized")) {
            logger.warn("DeepSeek session is not authorized. Re-login in browser and press Enter.");
            input.waitEnter();
            authGuard.ensureLoggedInOrWait(deepSeekPage, "retry after unauthorized");
        } else {
            logger.warn("Step failed: " + ex.getMessage());
        }

        if (failures > MAX_CONSECUTIVE_STEP_FAILURES) {
            logger.warn("Too many failures. Reset counter and keep retrying same step.");
            return 0;
        }
        return failures;
    }

    private String saveActiveSession(DeepSeekChatPage deepSeekPage, String task, boolean resumed, String previousChatId) {
        String currentChatId = deepSeekPage.getChatIdFromCurrentUrl();
        String currentUrl = deepSeekPage.getCurrentUrl();
        if (currentChatId == null || currentChatId.trim().isEmpty()) {
            return previousChatId;
        }

        try {
            sessions.saveSession(currentChatId, currentUrl, task, resumed);
            logger.info("Session saved: chatId=" + currentChatId);
        } catch (RuntimeException e) {
            logger.warn("Failed to save session: " + e.getMessage());
        }
        return currentChatId;
    }

    private OperationResult executeOperations(DeepSeekChatPage deepSeekPage, List<AiOperation> operations) {
        OperationResult result = OperationResult.continueWithoutPrompt();
        for (AiOperation operation : operations) {
            result = executeOperation(deepSeekPage, operation);
            if (result.isEnd() || result.hasNextPrompt()) {
                return result;
            }
        }
        return result;
    }

    private OperationResult executeOperation(DeepSeekChatPage deepSeekPage, AiOperation operation) {
        String type = operation.getType();
        String data = operation.getData();
        String content = operation.getContent();

        if ("END".equals(type)) {
            logger.block("AI_END", content == null ? "<empty>" : content);
            return OperationResult.end();
        }

        if ("CMD".equals(type) || "CMD_WAIT".equals(type)) {
            return executeCommand(type, data, content);
        }

        if (isFileOperation(type)) {
            return executeFileOperation(type, data, content);
        }

        if (isRagOperation(type)) {
            return executeRagOperation(type, data, content);
        }

        if ("HTTP_REQUEST".equals(type)) {
            return executeHttpRequest(data, content);
        }

        if (isNewChatOperation(type)) {
            return executeNewChat(deepSeekPage, type, data, content);
        }

        if (isChatCreatedGetOperation(type)) {
            return executeChatCreatedGet(deepSeekPage, type, data);
        }

        if ("TEXT".equals(type)) {
            return OperationResult.continueWithoutPrompt();
        }

        logger.warn("Unknown operation type: " + type);
        return OperationResult.continueWithoutPrompt();
    }

    private boolean isNewChatOperation(String type) {
        return type != null && ("NEW_CHAT".equals(type) || type.startsWith("NEW_CHAT#"));
    }

    private boolean isChatCreatedGetOperation(String type) {
        return type != null && ("CHAT_CREATED_GET".equals(type) || type.startsWith("CHAT_CREATED_GET#"));
    }

    private OperationResult executeNewChat(DeepSeekChatPage deepSeekPage, String type, String data, String content) {
        String id = operationId(type, data, "NEW_CHAT");
        if (id.isEmpty()) {
            String error = "PARALLEL_CHAT_ERROR\nNEW_CHAT\nERROR: id is empty";
            logger.block("PARALLEL_CHAT_RESULT", error);
            return OperationResult.nextPrompt(error);
        }
        if (content == null || content.trim().isEmpty()) {
            String error = "PARALLEL_CHAT_ERROR\nNEW_CHAT\nID: " + id + "\nERROR: content prompt is empty";
            logger.block("PARALLEL_CHAT_RESULT", error);
            return OperationResult.nextPrompt(error);
        }
        if (parallelChats.containsKey(id)) {
            String error = "PARALLEL_CHAT_ERROR\nNEW_CHAT\nID: " + id + "\nERROR: id already exists";
            logger.block("PARALLEL_CHAT_RESULT", error);
            return OperationResult.nextPrompt(error);
        }

        String parentHandle = deepSeekPage.getCurrentWindowHandle();
        try {
            String childHandle = deepSeekPage.openNewDeepSeekTab();
            authGuard.ensureLoggedInOrWait(deepSeekPage, "parallel chat " + id);
            deepSeekPage.startDeepSeekRequest(content);
            parallelChats.put(id, new ParallelChat(id, childHandle, content));
            deepSeekPage.switchToWindow(parentHandle);

            String result = "PARALLEL_CHAT_STARTED\nID: " + id + "\nSTATUS: RUNNING\nHANDLE: " + childHandle;
            logger.block("PARALLEL_CHAT_RESULT", result);
            return OperationResult.continueWithoutPrompt();
        } catch (RuntimeException e) {
            try {
                deepSeekPage.switchToWindow(parentHandle);
            } catch (RuntimeException ignored) {
            }
            String error = "PARALLEL_CHAT_ERROR\nNEW_CHAT\nID: " + id + "\nERROR: " + safeMessage(e);
            logger.block("PARALLEL_CHAT_RESULT", error);
            return OperationResult.nextPrompt(error);
        }
    }

    private OperationResult executeChatCreatedGet(DeepSeekChatPage deepSeekPage, String type, String data) {
        String id = operationId(type, data, "CHAT_CREATED_GET");
        ParallelChat chat = parallelChats.get(id);
        if (chat == null) {
            String error = "PARALLEL_CHAT_ERROR\nCHAT_CREATED_GET\nID: " + id + "\nERROR: chat not found";
            logger.block("PARALLEL_CHAT_RESULT", error);
            return OperationResult.nextPrompt(error);
        }

        String parentHandle = deepSeekPage.getCurrentWindowHandle();
        try {
            deepSeekPage.switchToWindow(chat.windowHandle);
            deepSeekPage.waitForResponseComplete();
            String response = deepSeekPage.getResponse();
            chat.status = "COMPLETED";
            chat.response = response == null ? "" : response;
            chat.chatId = deepSeekPage.getChatIdFromCurrentUrl();
            chat.url = deepSeekPage.getCurrentUrl();
            deepSeekPage.switchToWindow(parentHandle);

            String result = "CHAT_CREATED_GET#" + id + "\n"
                    + "STATUS: OK\n"
                    + "CHAT_STATUS: " + chat.status + "\n"
                    + "CHAT_ID: " + emptyToPlaceholder(chat.chatId) + "\n"
                    + "URL: " + emptyToPlaceholder(chat.url) + "\n"
                    + "RESPONSE:\n" + chat.response;
            logger.block("PARALLEL_CHAT_RESULT", result);
            return OperationResult.nextPrompt(result);
        } catch (RuntimeException e) {
            try {
                deepSeekPage.switchToWindow(parentHandle);
            } catch (RuntimeException ignored) {
            }
            chat.status = "FAILED";
            chat.response = safeMessage(e);
            String error = "CHAT_CREATED_GET#" + id + "\nSTATUS: FAILED\nERROR: " + chat.response;
            logger.block("PARALLEL_CHAT_RESULT", error);
            return OperationResult.nextPrompt(error);
        }
    }

    private String operationId(String type, String data, String prefix) {
        if (type != null && type.startsWith(prefix + "#")) {
            return type.substring((prefix + "#").length()).trim();
        }
        return data == null ? "" : data.trim();
    }

    private String safeMessage(RuntimeException e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private String emptyToPlaceholder(String value) {
        return value == null || value.trim().isEmpty() ? "<empty>" : value;
    }

    private OperationResult executeHttpRequest(String data, String content) {
        String result = httpRequestService.execute(data, content);
        logger.block("HTTP_REQUEST_RESULT", result);
        return OperationResult.nextPrompt(result);
    }

    private OperationResult executeCommand(String type, String data, String content) {
        String escapedContent = content == null ? null : StringEscapeUtils.escapeEcmaScript(content);
        String command = escapedContent == null ? data : data.replace("%s", escapedContent);
        logger.block("LOCAL_COMMAND", command);
        String cmdResult = commandService.executeWithCapture(command);
        logger.block("LOCAL_COMMAND_RESULT", cmdResult);

        if ("CMD_WAIT".equals(type) || failureDetector.isFailed(cmdResult)) {
            String nextPrompt = cmdResult == null || cmdResult.trim().isEmpty() ? AppConstants.CONTINUE_PROMPT : cmdResult;
            return OperationResult.nextPrompt(nextPrompt);
        }
        return OperationResult.continueWithoutPrompt();
    }

    private boolean isFileOperation(String type) {
        return "CREATE_FOLDER".equals(type)
                || "CREATE_AND_SAVE_FOLDER".equals(type)
                || "CREATE_AND_SAVE_FILE".equals(type)
                || "CHANGE_FILE".equals(type)
                || "READ_FILE".equals(type);
    }

    private boolean isRagOperation(String type) {
        return "ADD_RAG".equals(type)
                || "REMOVE_RAG".equals(type)
                || "SEARCH_RAG".equals(type)
                || "LIST_RAG".equals(type)
                || "ADD_GLOBAL_RAG".equals(type)
                || "REMOVE_GLOBAL_RAG".equals(type)
                || "SEARCH_GLOBAL_RAG".equals(type)
                || "LIST_GLOBAL_RAG".equals(type);
    }

    private OperationResult executeFileOperation(String type, String data, String content) {
        String result;
        if ("CREATE_FOLDER".equals(type) || "CREATE_AND_SAVE_FOLDER".equals(type)) {
            result = fileOperationService.createFolder(data);
        } else if ("CREATE_AND_SAVE_FILE".equals(type)) {
            result = fileOperationService.createAndSaveFile(data, content);
        } else if ("CHANGE_FILE".equals(type)) {
            result = fileOperationService.changeFile(data, content);
        } else {
            result = fileOperationService.readFile(data, content);
        }

        logger.block("FILE_OPERATION_RESULT", result);
        if ("READ_FILE".equals(type) || result.startsWith("FILE_OPERATION_ERROR")) {
            return OperationResult.nextPrompt(result);
        }
        return OperationResult.continueWithoutPrompt();
    }

    private OperationResult executeRagOperation(String type, String data, String content) {
        String result;
        if ("ADD_RAG".equals(type)) {
            result = ragOperationService.add(data, content);
        } else if ("ADD_GLOBAL_RAG".equals(type)) {
            result = ragOperationService.addGlobal(data, content);
        } else if ("REMOVE_RAG".equals(type)) {
            result = ragOperationService.remove(data, content);
        } else if ("REMOVE_GLOBAL_RAG".equals(type)) {
            result = ragOperationService.removeGlobal(data);
        } else if ("SEARCH_RAG".equals(type)) {
            result = ragOperationService.search(data, content);
        } else if ("SEARCH_GLOBAL_RAG".equals(type)) {
            result = ragOperationService.searchGlobal(data, content);
        } else if ("LIST_GLOBAL_RAG".equals(type)) {
            result = ragOperationService.listGlobal(content);
        } else {
            result = ragOperationService.list(content);
        }

        logger.block("RAG_OPERATION_RESULT", result);
        if ("SEARCH_RAG".equals(type)
                || "LIST_RAG".equals(type)
                || "SEARCH_GLOBAL_RAG".equals(type)
                || "LIST_GLOBAL_RAG".equals(type)
                || result.startsWith("RAG_OPERATION_ERROR")) {
            return OperationResult.nextPrompt(result);
        }
        return OperationResult.continueWithoutPrompt();
    }

    private static class OperationResult {

        private final boolean end;
        private final String nextPrompt;

        private OperationResult(boolean end, String nextPrompt) {
            this.end = end;
            this.nextPrompt = nextPrompt;
        }

        static OperationResult end() {
            return new OperationResult(true, null);
        }

        static OperationResult nextPrompt(String nextPrompt) {
            return new OperationResult(false, nextPrompt);
        }

        static OperationResult continueWithoutPrompt() {
            return new OperationResult(false, null);
        }

        boolean isEnd() {
            return end;
        }

        boolean hasNextPrompt() {
            return nextPrompt != null;
        }

        String getNextPrompt() {
            return nextPrompt;
        }
    }

    private static class ParallelChat {

        private final String id;
        private final String windowHandle;
        private final String prompt;
        private String status = "RUNNING";
        private String response = "";
        private String chatId = "";
        private String url = "";

        ParallelChat(String id, String windowHandle, String prompt) {
            this.id = id;
            this.windowHandle = windowHandle;
            this.prompt = prompt;
        }
    }
}
