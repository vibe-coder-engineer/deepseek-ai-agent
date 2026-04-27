package ru.sibgatulinanton.deepseek.dialog;

public enum PostDialogAction {
    CONTINUE("1"),
    NEW_DIALOG("2"),
    DELETE_DIALOG("3"),
    EXIT("4");

    private final String code;

    PostDialogAction(String code) {
        this.code = code;
    }

    public static PostDialogAction fromCode(String code) {
        for (PostDialogAction action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        return EXIT;
    }
}
