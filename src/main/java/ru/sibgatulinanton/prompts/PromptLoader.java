package ru.sibgatulinanton.prompts;

import ru.sibgatulinanton.lang.Language;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PromptLoader {
    public static final String promptFolder = "prompts";

    private final Map<Language, Map<String, String>> prompts = new HashMap<>();

    public PromptLoader() {
        init();
    }

    private void init() {
        for (Language language : Language.values()) {
            Map<String, String> languagePrompts = new HashMap<>();
            String languagePath = promptFolder + "/" + language.name().toLowerCase();

            try {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                String[] knownPrompts = {"system", "user", "assistant", "first_prompt", "default"};
                for (String promptName : knownPrompts) {
                    String fullPath = languagePath + "/" + promptName + ".txt";
                    try (InputStream is = classLoader.getResourceAsStream(fullPath)) {
                        if (is != null) {
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                                String content = reader.lines().collect(Collectors.joining("\n"));
                                languagePrompts.put(promptName, content);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error loading prompts for language " + language + ": " + e.getMessage());
            }

            prompts.put(language, languagePrompts);
        }
    }

    public String getPrompt(Language language, String promptName) {
        Map<String, String> languagePrompts = prompts.get(language);
        if (languagePrompts != null && languagePrompts.containsKey(promptName)) {
            return languagePrompts.get(promptName);
        }
        return null;
    }

    public String getPromptOrDefault(Language language, String promptName, String defaultValue) {
        String prompt = getPrompt(language, promptName);
        return prompt != null ? prompt : defaultValue;
    }

    public boolean hasPrompt(Language language, String promptName) {
        Map<String, String> languagePrompts = prompts.get(language);
        return languagePrompts != null && languagePrompts.containsKey(promptName);
    }

    public Map<String, String> getAllPrompts(Language language) {
        return new HashMap<>(prompts.getOrDefault(language, new HashMap<>()));
    }

    public void reload() {
        prompts.clear();
        init();
    }
}
