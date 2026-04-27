package ru.sibgatulinanton.os.cmd;

import ru.sibgatulinanton.os.OSType;

public class LocalCommandExecutorFactory {

    public LocalCommandExecutor create(OSType osType) {
        if (OSType.WINDOWS.equals(osType)) {
            return new PowerShellCommandExecutor();
        }
        return new ShellCommandExecutor();
    }
}
