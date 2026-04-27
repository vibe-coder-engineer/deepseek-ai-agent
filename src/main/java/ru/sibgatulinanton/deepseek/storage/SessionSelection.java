package ru.sibgatulinanton.deepseek.storage;

public class SessionSelection {

    public static SessionSelection newSession() {
        return new SessionSelection(false, null);
    }

    public static SessionSelection resume(SessionInfo session) {
        return new SessionSelection(true, session);
    }

    private final boolean resume;
    private final SessionInfo session;

    public SessionSelection(boolean resume, SessionInfo session) {
        this.resume = resume;
        this.session = session;
    }

    public boolean isResume() {
        return resume;
    }

    public SessionInfo getSession() {
        return session;
    }
}
