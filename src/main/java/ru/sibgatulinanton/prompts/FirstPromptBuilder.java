package ru.sibgatulinanton.prompts;

import ru.sibgatulinanton.AppConstants;
import ru.sibgatulinanton.lang.Language;
import ru.sibgatulinanton.os.OSType;

public class FirstPromptBuilder {

    private static final String FIRST_PROMPT_NAME = "first_prompt";
    private static final String DEFAULT_PROFILE = "default";

    private final PromptLoader promptLoader;

    public FirstPromptBuilder(PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
    }

    public String build(String task, OSType osType, String currentDir) {
        return build(task, osType, currentDir, DEFAULT_PROFILE);
    }

    public String build(String task, OSType osType, String currentDir, String profile) {
        String promptTemplate = resolvePromptTemplate(profile);
        if (promptTemplate == null) {
            throw new IllegalStateException("Prompt template '" + FIRST_PROMPT_NAME + "' not found for language RU");
        }

        String effectiveTask = task == null || task.trim().isEmpty() ? AppConstants.DEFAULT_TASK : task;
        return promptTemplate
                .replace("{TASK}", effectiveTask)
                .replace("{OS}", osType.name())
                .replace("{WORKSPACE}", currentDir)
                .replace("{CMD}", OSType.WINDOWS.equals(osType) ? "PowerShell" : "bash");
    }

    private String resolvePromptTemplate(String profile) {
        String profilePromptName = profilePromptName(profile);
        String profilePrompt = promptLoader.getPrompt(Language.RU, profilePromptName);
        if (profilePrompt != null && !profilePrompt.trim().isEmpty()) {
            return profilePrompt;
        }
        return promptLoader.getPrompt(Language.RU, FIRST_PROMPT_NAME);
    }

    private String profilePromptName(String profile) {
        String normalized = profile == null ? DEFAULT_PROFILE : profile.trim().toLowerCase().replace('-', '_');
        if (normalized.isEmpty() || DEFAULT_PROFILE.equals(normalized) || "standard".equals(normalized)) {
            return "profile_default";
        }
        if ("developer".equals(normalized) || "dev".equals(normalized)) {
            return "profile_developer";
        }
        if ("java_developer".equals(normalized) || "java".equals(normalized) || "java_dev".equals(normalized)) {
            return "profile_java_developer";
        }
        if ("architect".equals(normalized) || "architecture".equals(normalized)) {
            return "profile_architect";
        }
        if ("it_analyst".equals(normalized) || "analyst".equals(normalized) || "business_analyst".equals(normalized)) {
            return "profile_it_analyst";
        }
        if ("qa_usability_tester".equals(normalized)
                || "qa".equals(normalized)
                || "tester".equals(normalized)
                || "test".equals(normalized)
                || "usability_tester".equals(normalized)) {
            return "profile_qa_usability_tester";
        }
        return "profile_" + normalized;
    }
}
