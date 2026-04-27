package ru.sibgatulinanton.os.cmd;

public class PowerShellCommandExecutor implements LocalCommandExecutor {

    @Override
    public String execute(String command) throws Exception {
        return CompleteCmd.executePowerShell(command + "\n");
    }
}
