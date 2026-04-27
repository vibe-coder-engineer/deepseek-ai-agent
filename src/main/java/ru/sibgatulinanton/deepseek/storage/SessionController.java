package ru.sibgatulinanton.deepseek.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SessionController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path vibeDir;
    private final Path sessionsDir;
    private final Path currentSessionFile;
    private final ObjectMapper mapper;
    private final ObjectWriter prettyWriter;

    public SessionController() {
        this(Paths.get(System.getProperty("user.home"), ".vibecoder"));
    }

    public SessionController(Path vibeDir) {
        this.vibeDir = vibeDir;
        this.sessionsDir = vibeDir.resolve("sessions");
        this.currentSessionFile = vibeDir.resolve("current-session.txt");
        this.mapper = JsonMapper.builder().build();
        this.prettyWriter = mapper.writerWithDefaultPrettyPrinter();
    }

    public void ensureStorage() {
        try {
            Path legacyVibeDir = Paths.get(System.getProperty("user.dir"), ".vibecoder");
            if (Files.exists(legacyVibeDir) && !legacyVibeDir.equals(vibeDir) && !Files.exists(vibeDir)) {
                Files.createDirectories(vibeDir.getParent());
                Files.move(legacyVibeDir, vibeDir, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.createDirectories(sessionsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create .vibecoder storage", e);
        }
    }

    public List<SessionInfo> listSessions() {
        List<SessionInfo> sessions = new ArrayList<SessionInfo>();
        if (!Files.exists(sessionsDir)) {
            return sessions;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionsDir, "*.json")) {
            for (Path file : stream) {
                SessionInfo info = readSession(file);
                if (info != null && !info.getChatId().isEmpty() && !info.getUrl().isEmpty()) {
                    sessions.add(info);
                }
            }
        } catch (IOException ignored) {
        }

        sessions.sort(new Comparator<SessionInfo>() {
            @Override
            public int compare(SessionInfo a, SessionInfo b) {
                return b.getLastUsedAt().compareTo(a.getLastUsedAt());
            }
        });

        return sessions;
    }

    public boolean deleteSession(String chatId) throws IOException {
        if (chatId == null || chatId.trim().isEmpty()) {
            return false;
        }

        Path file = sessionsDir.resolve(chatId + ".json");
        boolean deleted = Files.deleteIfExists(file);
        clearCurrentSession(chatId);
        return deleted;
    }

    public void saveSession(String chatId, String url, String task, boolean resumed) {
        if (chatId == null || chatId.trim().isEmpty() || url == null || url.trim().isEmpty()) {
            return;
        }

        String now = LocalDateTime.now().format(TS);
        Path sessionFile = sessionsDir.resolve(chatId + ".json");
        ObjectNode json = readSessionObject(sessionFile);

        if (!json.has("createdAt")) {
            json.put("createdAt", now);
        }

        json.put("chatId", chatId);
        json.put("url", url);
        json.put("task", task == null ? "" : task);
        json.put("lastUsedAt", now);
        json.put("resumed", resumed);

        try {
            Files.write(sessionFile, prettyWriter.writeValueAsBytes(json));
            Files.write(currentSessionFile, chatId.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save session", e);
        }
    }

    private SessionInfo readSession(Path file) {
        try {
            JsonNode json = mapper.readTree(file.toFile());
            return new SessionInfo(textValue(json.get("chatId")),
                    textValue(json.get("url")),
                    textValue(json.get("task")),
                    textValue(json.get("lastUsedAt"))
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private ObjectNode readSessionObject(Path file) {
        if (!Files.exists(file)) {
            return mapper.createObjectNode();
        }

        try {
            JsonNode json = mapper.readTree(file.toFile());
            return json != null && json.isObject() ? (ObjectNode) json : mapper.createObjectNode();
        } catch (Exception ignored) {
            return mapper.createObjectNode();
        }
    }

    private void clearCurrentSession(String chatId) {
        try {
            if (!Files.exists(currentSessionFile)) {
                return;
            }

            String current = new String(Files.readAllBytes(currentSessionFile), StandardCharsets.UTF_8).trim();
            if (chatId.equals(current)) {
                Files.delete(currentSessionFile);
            }
        } catch (Exception ignored) {
        }
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }
}
