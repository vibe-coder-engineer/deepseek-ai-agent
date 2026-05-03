package ru.sibgatulinanton.cli;

public class AppArguments {

    private static final String HEADLESS_ARG = "--headless";
    private static final String EXEC_ARG = "--exec";
    private static final String THREAD_ARG = "--thread";
    private static final String PROFILE_ARG = "--profile";

    private final boolean headless;
    private final String execPrompt;
    private final String thread;
    private final String profile;

    private AppArguments(boolean headless, String execPrompt, String thread, String profile) {
        this.headless = headless;
        this.execPrompt = execPrompt;
        this.thread = thread;
        this.profile = profile;
    }

    public static AppArguments parse(String[] args) {
        return new AppArguments(
                hasArg(args, HEADLESS_ARG),
                getArgValue(args, EXEC_ARG),
                getArgValue(args, THREAD_ARG),
                getArgValue(args, PROFILE_ARG)
        );
    }

    public boolean isHeadless() {
        return headless;
    }

    public boolean isExecMode() {
        return execPrompt != null && !execPrompt.trim().isEmpty();
    }

    public String getExecPrompt() {
        return execPrompt;
    }

    public String getThread() {
        return thread;
    }

    public boolean hasThread() {
        return thread != null && !thread.trim().isEmpty();
    }

    public String getProfile() {
        return profile == null || profile.trim().isEmpty() ? "default" : profile.trim();
    }

    private static boolean hasArg(String[] args, String targetArg) {
        if (args == null || targetArg == null || targetArg.trim().isEmpty()) {
            return false;
        }

        for (String arg : args) {
            if (targetArg.equalsIgnoreCase(arg)) {
                return true;
            }
        }

        return false;
    }

    private static String getArgValue(String[] args, String targetArg) {
        if (args == null || targetArg == null || targetArg.trim().isEmpty()) {
            return null;
        }

        String prefix = targetArg + "=";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            }

            if (targetArg.equalsIgnoreCase(arg)) {
                return i + 1 < args.length ? args[i + 1] : null;
            }

            if (arg.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return arg.substring(prefix.length());
            }
        }

        return null;
    }
}
