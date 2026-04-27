package ru.sibgatulinanton.deepseek.storage;

public class SessionInfo {

    private String chatId;
    private String url;
    private String task;
    private String lastUsedAt;

    public SessionInfo(String chatId, String url, String task, String lastUsedAt) {
        this.chatId = chatId;
        this.url = url;
        this.task = task;
        this.lastUsedAt = lastUsedAt;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public void setLastUsedAt(String lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public String getChatId() {
        return chatId;
    }

    public String getUrl() {
        return url;
    }

    public String getTask() {
        return task;
    }

    public String getLastUsedAt() {
        return lastUsedAt;
    }
}