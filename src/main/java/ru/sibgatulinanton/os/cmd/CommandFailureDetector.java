package ru.sibgatulinanton.os.cmd;

public class CommandFailureDetector {

    public boolean isFailed(String commandResult) {
        if (commandResult == null) {
            return false;
        }

        String lower = commandResult.toLowerCase();
        return lower.contains("command_error")
                || lower.contains("exception")
                || lower.contains("ошибка")
                || lower.contains("error");
    }
}
