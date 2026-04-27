package ru.sibgatulinanton.deepseek.dialog;

import org.apache.commons.lang3.StringEscapeUtils;
import ru.sibgatulinanton.AppConstants;
import ru.sibgatulinanton.cli.ConsoleInput;
import ru.sibgatulinanton.deepseek.DeepSeekAuthGuard;
import ru.sibgatulinanton.deepseek.DeepSeekChatPage;
import ru.sibgatulinanton.deepseek.storage.SessionController;
import ru.sibgatulinanton.files.FileOperationService;
import ru.sibgatulinanton.logging.ConsoleLogger;
import ru.sibgatulinanton.os.cmd.CommandFailureDetector;
import ru.sibgatulinanton.os.cmd.LocalCommandService;
import ru.sibgatulinanton.rag.RagOperationService;

import java.util.List;

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

    public DeepSeekDialogRunner(ConsoleInput input,
                                ConsoleLogger logger,
                                DeepSeekAuthGuard authGuard,
                                SessionController sessions,
                                AiResponseParser responseParser,
                                LocalCommandService commandService,
                                CommandFailureDetector failureDetector,
                                FileOperationService fileOperationService,
                                RagOperationService ragOperationService) {
        this.input = input;
        this.logger = logger;
        this.authGuard = authGuard;
        this.sessions = sessions;
        this.responseParser = responseParser;
        this.commandService = commandService;
        this.failureDetector = failureDetector;
        this.fileOperationService = fileOperationService;
        this.ragOperationService = ragOperationService;
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

            OperationResult result = executeOperations(operations);
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

    private OperationResult executeOperations(List<AiOperation> operations) {
        OperationResult result = OperationResult.continueWithoutPrompt();
        for (AiOperation operation : operations) {
            result = executeOperation(operation);
            if (result.isEnd() || result.hasNextPrompt()) {
                return result;
            }
        }
        return result;
    }

    private OperationResult executeOperation(AiOperation operation) {
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

        if ("TEXT".equals(type)) {
            return OperationResult.continueWithoutPrompt();
        }

        logger.warn("Unknown operation type: " + type);
        return OperationResult.continueWithoutPrompt();
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
}
