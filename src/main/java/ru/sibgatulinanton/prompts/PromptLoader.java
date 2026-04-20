package ru.sibgatulinanton.prompts;

import ru.sibgatulinanton.lang.Language;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class PromptLoader {
    public static final String promptFolder = "resources/prompts";

    private final Map<Language, Map<String, String>> prompts = new HashMap<>();

    public PromptLoader() {
        init();
    }

    private void init() {
        for (Language language : Language.values()) {
            Map<String, String> languagePrompts = new HashMap<>();
            String languagePath = promptFolder + File.separator + language.name().toLowerCase();

            try {
                Path dirPath = Paths.get(languagePath);
                if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.txt")) {
                        for (Path filePath : stream) {
                            String fileName = filePath.getFileName().toString();
                            String promptName = fileName.substring(0, fileName.lastIndexOf('.'));
                            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                            languagePrompts.put(promptName, content);
                        }
                    }
                }
            } catch (IOException e) {
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