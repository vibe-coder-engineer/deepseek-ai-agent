package ru.sibgatulinanton.os.cmd;

public class LocalCommandService {

    private final LocalCommandExecutor executor;

    public LocalCommandService(LocalCommandExecutor executor) {
        this.executor = executor;
    }

    public String executeWithCapture(String command) {
        try {
            return executor.execute(command);
        } catch (Exception ex) {
            String error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            return "COMMAND_ERROR\nFAILED_COMMAND:\n" + command + "\nERROR:\n" + error;
        }
    }
}
