package ru.sibgatulinanton.deepseek;

import ru.sibgatulinanton.cli.ConsoleInput;
import ru.sibgatulinanton.logging.ConsoleLogger;

public class DeepSeekAuthGuard {

    private static final int LOGIN_CHECK_ATTEMPTS = 20;
    private static final int LOGIN_CHECK_DELAY_MS = 500;

    private final ConsoleInput input;
    private final ConsoleLogger logger;

    public DeepSeekAuthGuard(ConsoleInput input, ConsoleLogger logger) {
        this.input = input;
        this.logger = logger;
    }

    public void ensureLoggedInOrWait(DeepSeekChatPage deepSeekPage, String stage) {
        while (!deepSeekPage.isUserLoggedIn()) {
            logger.warn("User is NOT authenticated in DeepSeek (" + stage + "). Authenticate in browser and press Enter.");
            input.waitEnter();

            for (int i = 0; i < LOGIN_CHECK_ATTEMPTS; i++) {
                if (deepSeekPage.isUserLoggedIn()) {
                    break;
                }
                sleep();
            }
        }
        logger.info("User is authenticated in DeepSeek (" + stage + ")");
    }

    private void sleep() {
        try {
            Thread.sleep(LOGIN_CHECK_DELAY_MS);
        } catch (InterruptedException ignored) {
        }
    }
}
