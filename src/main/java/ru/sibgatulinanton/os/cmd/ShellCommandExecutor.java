package ru.sibgatulinanton.os.cmd;

public class ShellCommandExecutor implements LocalCommandExecutor {

    @Override
    public String execute(String command) throws Exception {
        return CompleteCmd.executeCommand(command);
    }
}
