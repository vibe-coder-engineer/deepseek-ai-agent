package ru.sibgatulinanton.deepseek;

import ru.sibgatulinanton.AppConstants;

public class DeepSeekUrlFactory {

    public String toThreadUrl(String threadArg) {
        if (threadArg == null || threadArg.trim().isEmpty()) {
            return BrowserDriverManager.CHAT_DEEPSEEK_LINK;
        }

        String trimmed = threadArg.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        return BrowserDriverManager.CHAT_DEEPSEEK_LINK + AppConstants.CHAT_PATH + trimmed;
    }
}
