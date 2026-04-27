package ru.sibgatulinanton.deepseek.dialog;

import ru.sibgatulinanton.cli.ConsoleInput;
import ru.sibgatulinanton.logging.ConsoleLogger;

public class PostDialogMenu {

    private final ConsoleInput input;
    private final ConsoleLogger logger;

    public PostDialogMenu(ConsoleInput input, ConsoleLogger logger) {
        this.input = input;
        this.logger = logger;
    }

    public PostDialogAction askAction() {
        logger.info("Dialog finished. Choose action:");
        logger.info("1 - continue this dialog");
        logger.info("2 - start new dialog");
        logger.info("3 - delete dialog(s)");
        logger.info("4 - exit");

        String action = input.askLine("Enter action (1/2/3/4): ").trim();
        return PostDialogAction.fromCode(action);
    }
}
