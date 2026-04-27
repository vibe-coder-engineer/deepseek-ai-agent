package ru.sibgatulinanton.deepseek.dialog;

public class DialogRunResult {

    final String activeChatId;

    public DialogRunResult(String activeChatId) {
        this.activeChatId = activeChatId;
    }

    public String getActiveChatId() {
        return activeChatId;
    }
}
