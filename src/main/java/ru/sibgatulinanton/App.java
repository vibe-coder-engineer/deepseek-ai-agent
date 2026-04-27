package ru.sibgatulinanton;

import ru.sibgatulinanton.cli.AppArguments;
import ru.sibgatulinanton.cli.ConsoleInput;
import ru.sibgatulinanton.deepseek.BrowserDriverManager;
import ru.sibgatulinanton.deepseek.DeepSeekAuthGuard;
import ru.sibgatulinanton.deepseek.DeepSeekChatPage;
import ru.sibgatulinanton.deepseek.DeepSeekUrlFactory;
import ru.sibgatulinanton.deepseek.dialog.AiResponseParser;
import ru.sibgatulinanton.deepseek.dialog.DeepSeekDialogRunner;
import ru.sibgatulinanton.deepseek.dialog.DialogRunResult;
import ru.sibgatulinanton.deepseek.dialog.PostDialogAction;
import ru.sibgatulinanton.deepseek.dialog.PostDialogMenu;
import ru.sibgatulinanton.deepseek.storage.SessionConsoleController;
import ru.sibgatulinanton.deepseek.storage.SessionController;
import ru.sibgatulinanton.deepseek.storage.SessionSelection;
import ru.sibgatulinanton.files.FileOperationService;
import ru.sibgatulinanton.logging.ConsoleLogger;
import ru.sibgatulinanton.os.OSType;
import ru.sibgatulinanton.os.OSUtils;
import ru.sibgatulinanton.os.cmd.CommandFailureDetector;
import ru.sibgatulinanton.os.cmd.CompleteCmd;
import ru.sibgatulinanton.os.cmd.LocalCommandExecutor;
import ru.sibgatulinanton.os.cmd.LocalCommandExecutorFactory;
import ru.sibgatulinanton.os.cmd.LocalCommandService;
import ru.sibgatulinanton.prompts.FirstPromptBuilder;
import ru.sibgatulinanton.prompts.PromptLoader;
import ru.sibgatulinanton.rag.RagOperationService;

import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class App {

    public static void main(String[] args) {
        new App().run(AppArguments.parse(args));
    }

    private void run(AppArguments args) {
        ConsoleLogger logger = new ConsoleLogger();
        OSType osType = OSUtils.getOperatingSystemType();
        SessionController sessions = new SessionController();
        sessions.ensureStorage();

        logger.info("DeepSeek agent started in console mode");
        logger.info("Browser mode: " + (args.isHeadless() ? "headless" : "headed"));
        if (args.isExecMode()) {
            logger.info("Exec mode enabled");
        }

        BrowserDriverManager manager = new BrowserDriverManager(args.isHeadless());
        AtomicBoolean driverClosed = new AtomicBoolean(false);
        registerShutdownHook(manager, driverClosed);

        try (Scanner scanner = new Scanner(System.in)) {
            AppRuntime runtime = createRuntime(scanner, logger, sessions, osType);
            DeepSeekChatPage deepSeekPage = new DeepSeekChatPage(manager);

            if (args.isExecMode()) {
                runExecMode(args, manager, deepSeekPage, runtime, osType);
                logger.info("Exec mode completed. Exit.");
                System.exit(0);
            }

            runInteractiveMode(manager, deepSeekPage, runtime, osType);
            logger.info("App finished. Press Enter to exit.");
            runtime.input.waitEnter();
        } finally {
            safeQuit(manager, driverClosed);
        }
    }

    private AppRuntime createRuntime(Scanner scanner, ConsoleLogger logger, SessionController sessions, OSType osType) {
        ConsoleInput input = new ConsoleInput(scanner);
        DeepSeekAuthGuard authGuard = new DeepSeekAuthGuard(input, logger);
        LocalCommandExecutor executor = new LocalCommandExecutorFactory().create(osType);
        LocalCommandService commandService = new LocalCommandService(executor);
        java.nio.file.Path workspace = Paths.get(System.getProperty("user.dir"));
        DeepSeekDialogRunner dialogRunner = new DeepSeekDialogRunner(
                input,
                logger,
                authGuard,
                sessions,
                new AiResponseParser(),
                commandService,
                new CommandFailureDetector(),
                new FileOperationService(workspace),
                new RagOperationService(workspace)
        );

        return new AppRuntime(input,
                authGuard,
                dialogRunner,
                new SessionConsoleController(sessions, input, logger),
                new PostDialogMenu(input, logger),
                new FirstPromptBuilder(new PromptLoader()),
                new DeepSeekUrlFactory());
    }

    private void runExecMode(AppArguments args,
                             BrowserDriverManager manager,
                             DeepSeekChatPage deepSeekPage,
                             AppRuntime runtime,
                             OSType osType) {
        String execPrompt = args.getExecPrompt();
        if (args.hasThread()) {
            manager.openUrl(runtime.urlFactory.toThreadUrl(args.getThread()));
            runtime.authGuard.ensureLoggedInOrWait(deepSeekPage, "exec-thread");
        } else {
            manager.openDeepSeek();
            runtime.authGuard.ensureLoggedInOrWait(deepSeekPage, "exec");
            execPrompt = runtime.promptBuilder.build(args.getExecPrompt(), osType, System.getProperty("user.dir"));
        }

        manager.driverWait(30);
        runtime.dialogRunner.runUntilEnd(deepSeekPage, execPrompt, args.getExecPrompt(), args.hasThread());
    }

    private void runInteractiveMode(BrowserDriverManager manager,
                                    DeepSeekChatPage deepSeekPage,
                                    AppRuntime runtime,
                                    OSType osType) {
        manager.openDeepSeek();
        runtime.authGuard.ensureLoggedInOrWait(deepSeekPage, "startup");
        manager.driverWait(30);

        InteractiveState state = new InteractiveState();
        boolean appRunning = true;
        while (appRunning) {
            prepareDialogState(manager, deepSeekPage, runtime, osType, state);
            if (state.selection == null) {
                break;
            }

            DialogRunResult runResult = runtime.dialogRunner.runUntilEnd(
                    deepSeekPage,
                    state.prompt,
                    state.task,
                    state.selection.isResume()
            );
            state.updateActiveChatId(runResult.getActiveChatId());

            appRunning = handlePostDialogAction(manager, deepSeekPage, runtime, state);
        }
    }

    private void prepareDialogState(BrowserDriverManager manager,
                                    DeepSeekChatPage deepSeekPage,
                                    AppRuntime runtime,
                                    OSType osType,
                                    InteractiveState state) {
        if (state.selection != null) {
            return;
        }

        state.selection = runtime.sessionsMenu.chooseSession();
        if (state.selection == null) {
            return;
        }

        if (state.selection.isResume()) {
            manager.openUrl(state.selection.getSession().getUrl());
            runtime.authGuard.ensureLoggedInOrWait(deepSeekPage, "resume");
            state.task = state.selection.getSession().getTask();
            state.activeChatId = state.selection.getSession().getChatId();
            state.prompt = askPromptOrContinue(runtime, "Enter message for resumed dialog (Enter = 'continue'): ");
            return;
        }

        state.task = runtime.input.askLine("Enter task for DeepSeek: ");
        if (state.task.trim().isEmpty()) {
            state.task = AppConstants.DEFAULT_TASK;
        }
        state.prompt = runtime.promptBuilder.build(state.task, osType, System.getProperty("user.dir"));
    }

    private boolean handlePostDialogAction(BrowserDriverManager manager,
                                           DeepSeekChatPage deepSeekPage,
                                           AppRuntime runtime,
                                           InteractiveState state) {
        PostDialogAction action = runtime.postDialogMenu.askAction();
        if (PostDialogAction.CONTINUE.equals(action)) {
            openActiveDialog(manager, deepSeekPage, runtime, state.activeChatId);
            state.prompt = askPromptOrContinue(runtime, "Enter message for this dialog (Enter = 'continue'): ");
            return true;
        }

        if (PostDialogAction.NEW_DIALOG.equals(action)) {
            state.reset();
            manager.openDeepSeek();
            runtime.authGuard.ensureLoggedInOrWait(deepSeekPage, "new dialog");
            return true;
        }

        if (PostDialogAction.DELETE_DIALOG.equals(action)) {
            runtime.sessionsMenu.deleteDialog(state.activeChatId);
            state.reset();
            manager.openDeepSeek();
            runtime.authGuard.ensureLoggedInOrWait(deepSeekPage, "after delete");
            return true;
        }

        return false;
    }

    private void openActiveDialog(BrowserDriverManager manager,
                                  DeepSeekChatPage deepSeekPage,
                                  AppRuntime runtime,
                                  String activeChatId) {
        if (activeChatId == null || activeChatId.trim().isEmpty()) {
            return;
        }

        manager.openUrl(runtime.urlFactory.toThreadUrl(activeChatId));
        runtime.authGuard.ensureLoggedInOrWait(deepSeekPage, "post-end continue");
    }

    private String askPromptOrContinue(AppRuntime runtime, String promptText) {
        String prompt = runtime.input.askLine(promptText);
        return prompt.trim().isEmpty() ? AppConstants.CONTINUE_PROMPT : prompt;
    }

    private void registerShutdownHook(final BrowserDriverManager manager, final AtomicBoolean driverClosed) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                safeQuit(manager, driverClosed);
                CompleteCmd.closePowerShell();
            }
        }, "deepseek-driver-shutdown"));
    }

    private void safeQuit(BrowserDriverManager manager, AtomicBoolean driverClosed) {
        if (manager == null || driverClosed == null) {
            return;
        }
        if (!driverClosed.compareAndSet(false, true)) {
            return;
        }
        try {
            manager.quit();
        } catch (Exception ignored) {
        }
    }

    private static class AppRuntime {

        private final ConsoleInput input;
        private final DeepSeekAuthGuard authGuard;
        private final DeepSeekDialogRunner dialogRunner;
        private final SessionConsoleController sessionsMenu;
        private final PostDialogMenu postDialogMenu;
        private final FirstPromptBuilder promptBuilder;
        private final DeepSeekUrlFactory urlFactory;

        AppRuntime(ConsoleInput input,
                   DeepSeekAuthGuard authGuard,
                   DeepSeekDialogRunner dialogRunner,
                   SessionConsoleController sessionsMenu,
                   PostDialogMenu postDialogMenu,
                   FirstPromptBuilder promptBuilder,
                   DeepSeekUrlFactory urlFactory) {
            this.input = input;
            this.authGuard = authGuard;
            this.dialogRunner = dialogRunner;
            this.sessionsMenu = sessionsMenu;
            this.postDialogMenu = postDialogMenu;
            this.promptBuilder = promptBuilder;
            this.urlFactory = urlFactory;
        }
    }

    private static class InteractiveState {

        private SessionSelection selection;
        private String task;
        private String prompt;
        private String activeChatId;

        void updateActiveChatId(String activeChatId) {
            if (activeChatId != null && !activeChatId.trim().isEmpty()) {
                this.activeChatId = activeChatId;
            }
        }

        void reset() {
            selection = null;
            task = null;
            prompt = null;
            activeChatId = null;
        }
    }
}
