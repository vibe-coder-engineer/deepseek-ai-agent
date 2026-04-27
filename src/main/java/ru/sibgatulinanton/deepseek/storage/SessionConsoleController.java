package ru.sibgatulinanton.deepseek.storage;

import ru.sibgatulinanton.cli.ConsoleInput;
import ru.sibgatulinanton.logging.ConsoleLogger;

import java.util.List;

public class SessionConsoleController {

    private static final String MODE_NEW_DIALOG = "1";
    private static final String MODE_RESUME_DIALOG = "2";
    private static final String MODE_DELETE_DIALOG = "3";
    private static final String MODE_EXIT = "4";

    private final SessionController sessions;
    private final ConsoleInput input;
    private final ConsoleLogger logger;

    public SessionConsoleController(SessionController sessions, ConsoleInput input, ConsoleLogger logger) {
        this.sessions = sessions;
        this.input = input;
        this.logger = logger;
    }

    public SessionSelection chooseSession() {
        while (true) {
            List<SessionInfo> savedSessions = sessions.listSessions();
            printModeMenu();

            String mode = input.askLine("Enter mode (1/2/3/4): ").trim();
            if (MODE_NEW_DIALOG.equals(mode)) {
                return SessionSelection.newSession();
            }

            if (MODE_RESUME_DIALOG.equals(mode)) {
                SessionSelection selection = chooseSavedSession(savedSessions);
                if (selection != null) {
                    return selection;
                }
                continue;
            }

            if (MODE_DELETE_DIALOG.equals(mode)) {
                deleteDialog(null);
                continue;
            }

            if (MODE_EXIT.equals(mode)) {
                return null;
            }

            logger.warn("Unknown mode.");
        }
    }

    public void deleteDialog(String preferredChatId) {
        List<SessionInfo> savedSessions = sessions.listSessions();
        if (savedSessions.isEmpty()) {
            logger.warn("No saved dialogs to delete.");
            return;
        }

        logger.info("Delete dialog mode:");
        printSessions(savedSessions, preferredChatId);

        String selected = input.askLine("Enter number to delete (or empty to cancel): ").trim();
        if (selected.isEmpty()) {
            logger.info("Delete canceled.");
            return;
        }

        try {
            int index = Integer.parseInt(selected) - 1;
            if (index < 0 || index >= savedSessions.size()) {
                logger.warn("Invalid selection.");
                return;
            }

            SessionInfo target = savedSessions.get(index);
            if (sessions.deleteSession(target.getChatId())) {
                logger.info("Dialog deleted: " + target.getChatId());
            } else {
                logger.warn("Dialog file not found: " + target.getChatId());
            }
        } catch (Exception e) {
            logger.warn("Delete failed: " + e.getMessage());
        }
    }

    private SessionSelection chooseSavedSession(List<SessionInfo> savedSessions) {
        if (savedSessions.isEmpty()) {
            logger.warn("No saved sessions found.");
            return null;
        }

        logger.info("Saved sessions:");
        printSessions(savedSessions, null);

        String selected = input.askLine("Enter session number: ");
        try {
            int index = Integer.parseInt(selected) - 1;
            if (index >= 0 && index < savedSessions.size()) {
                return SessionSelection.resume(savedSessions.get(index));
            }
        } catch (Exception ignored) {
        }

        logger.warn("Invalid session number.");
        return null;
    }

    private void printModeMenu() {
        logger.info("Choose mode:");
        logger.info("1 - start NEW dialog");
        logger.info("2 - RESUME existing dialog");
        logger.info("3 - DELETE saved dialog");
        logger.info("4 - EXIT app");
    }

    private void printSessions(List<SessionInfo> savedSessions, String preferredChatId) {
        for (int i = 0; i < savedSessions.size(); i++) {
            SessionInfo session = savedSessions.get(i);
            String mark = preferredChatId != null && preferredChatId.equals(session.getChatId()) ? " *current" : "";
            logger.info((i + 1) + ") " + session.getChatId() + mark
                    + " | lastUsed=" + session.getLastUsedAt()
                    + " | task=" + session.getTask());
        }
    }
}
