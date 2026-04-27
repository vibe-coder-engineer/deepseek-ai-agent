package ru.sibgatulinanton.prompts;

import ru.sibgatulinanton.AppConstants;
import ru.sibgatulinanton.lang.Language;
import ru.sibgatulinanton.os.OSType;

public class FirstPromptBuilder {

    private static final String FIRST_PROMPT_NAME = "first_prompt";
    private static final String FIRST_PROMPT_PATH = "resources/prompts/ru/first_prompt.txt";

    private final PromptLoader promptLoader;

    public FirstPromptBuilder(PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
    }

    public String build(String task, OSType osType, String currentDir) {
        String firstPromptTemplate = promptLoader.getPrompt(Language.RU, FIRST_PROMPT_NAME);
        if (firstPromptTemplate == null) {
            throw new IllegalStateException("Prompt template " + FIRST_PROMPT_PATH + " not found");
        }

        String effectiveTask = task == null || task.trim().isEmpty() ? AppConstants.DEFAULT_TASK : task;
        return firstPromptTemplate
                .replace("{TASK}", effectiveTask)
                .replace("{OS}", osType.name())
                .replace("{WORKSPACE}", currentDir)
                .replace("{CMD}", OSType.WINDOWS.equals(osType) ? "PowerShell" : "bash");
    }
}
