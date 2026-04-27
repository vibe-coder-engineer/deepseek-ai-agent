package ru.sibgatulinanton.deepseek.dialog;

public class AiOperation {

    private final String type;
    private final String data;
    private final String content;

    public AiOperation(String type, String data, String content) {
        this.type = type == null ? "" : type;
        this.data = data == null ? "" : data;
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public String getData() {
        return data;
    }

    public String getContent() {
        return content;
    }
}
