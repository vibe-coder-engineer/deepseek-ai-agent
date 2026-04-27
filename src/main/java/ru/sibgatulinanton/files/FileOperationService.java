package ru.sibgatulinanton.files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class FileOperationService {

    private static final int DEFAULT_MAX_LINES = 200;
    private static final int DEFAULT_CONTEXT_LINES = 3;
    private static final int DEFAULT_MAX_MATCHES = 20;

    private final Path workspace;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public FileOperationService(Path workspace) {
        this.workspace = workspace.toAbsolutePath().normalize();
    }

    public String createFolder(String path) {
        try {
            Path target = resolvePath(path);
            Files.createDirectories(target);
            return "FILE_OPERATION_OK\nCREATE_FOLDER\nPATH: " + target;
        } catch (Exception e) {
            return error("CREATE_FOLDER", path, e);
        }
    }

    public String createAndSaveFile(String path, String content) {
        try {
            Path target = resolvePath(path);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.write(target, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return "FILE_OPERATION_OK\nCREATE_AND_SAVE_FILE\nPATH: " + target;
        } catch (Exception e) {
            return error("CREATE_AND_SAVE_FILE", path, e);
        }
    }

    public String changeFile(String path, String instructionJson) {
        try {
            Path target = resolvePath(path);
            JsonNode instruction = readInstruction(instructionJson);

            if (instruction.has("startLine")) {
                return changeLines(target, instruction);
            }

            if (instruction.has("oldText")) {
                return replaceText(target, instruction);
            }

            return "FILE_OPERATION_ERROR\nCHANGE_FILE\nPATH: " + target
                    + "\nERROR: content must contain startLine/endLine/replacement or oldText/newText";
        } catch (Exception e) {
            return error("CHANGE_FILE", path, e);
        }
    }

    public String readFile(String path, String optionsJson) {
        try {
            Path target = resolvePath(path);
            JsonNode options = readOptionalInstruction(optionsJson);
            if (options.has("contains")) {
                return readMatches(target, options);
            }
            return readRange(target, options);
        } catch (Exception e) {
            return error("READ_FILE", path, e);
        }
    }

    private String changeLines(Path target, JsonNode instruction) throws IOException {
        int startLine = positiveInt(instruction, "startLine", 1);
        int endLine = positiveInt(instruction, "endLine", startLine);
        String replacement = text(instruction, "replacement", "");

        if (startLine > endLine) {
            return "FILE_OPERATION_ERROR\nCHANGE_FILE\nPATH: " + target + "\nERROR: invalid line range";
        }

        List<String> replacementLines = splitLines(replacement);
        Path tempFile = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        boolean inserted = false;
        int lineNumber = 0;

        try (BufferedReader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == startLine) {
                    writeLines(writer, replacementLines);
                    inserted = true;
                }

                if (lineNumber >= startLine && lineNumber <= endLine) {
                    continue;
                }

                writer.write(line);
                writer.newLine();
            }

            if (!inserted && startLine == lineNumber + 1) {
                writeLines(writer, replacementLines);
                inserted = true;
            }
        }

        if (!inserted) {
            Files.deleteIfExists(tempFile);
            return "FILE_OPERATION_ERROR\nCHANGE_FILE\nPATH: " + target
                    + "\nERROR: startLine is outside file. totalLines=" + lineNumber;
        }

        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        return "FILE_OPERATION_OK\nCHANGE_FILE\nPATH: " + target
                + "\nLINES: " + startLine + "-" + endLine
                + "\nINSERTED_LINES: " + replacementLines.size();
    }

    private String replaceText(Path target, JsonNode instruction) throws IOException {
        String oldText = text(instruction, "oldText", null);
        String newText = text(instruction, "newText", "");
        boolean replaceAll = instruction.has("replaceAll") && instruction.get("replaceAll").asBoolean(false);
        if (oldText == null || oldText.isEmpty()) {
            return "FILE_OPERATION_ERROR\nCHANGE_FILE\nPATH: " + target + "\nERROR: oldText is empty";
        }

        String content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        if (!content.contains(oldText)) {
            return "FILE_OPERATION_ERROR\nCHANGE_FILE\nPATH: " + target + "\nERROR: oldText not found";
        }

        String updated = replaceAll ? content.replace(oldText, newText) : content.replaceFirst(quoteRegex(oldText), quoteReplacement(newText));
        Files.write(target, updated.getBytes(StandardCharsets.UTF_8));
        return "FILE_OPERATION_OK\nCHANGE_FILE\nPATH: " + target
                + "\nMODE: " + (replaceAll ? "replaceAll" : "replaceFirst");
    }

    private String readRange(Path target, JsonNode options) throws IOException {
        int startLine = positiveInt(options, "startLine", 1);
        int maxLines = positiveInt(options, "maxLines", DEFAULT_MAX_LINES);

        StringBuilder result = new StringBuilder();
        result.append("FILE_READ_RESULT\nPATH: ").append(target)
                .append("\nRANGE_START: ").append(startLine)
                .append("\nMAX_LINES: ").append(maxLines)
                .append("\nCONTENT:\n");

        int lineNumber = 0;
        int emitted = 0;
        try (BufferedReader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber < startLine) {
                    continue;
                }
                if (emitted >= maxLines) {
                    break;
                }
                result.append(lineNumber).append(": ").append(line).append('\n');
                emitted++;
            }
        }

        result.append("EMITTED_LINES: ").append(emitted).append('\n');
        return result.toString();
    }

    private String readMatches(Path target, JsonNode options) throws IOException {
        String needle = text(options, "contains", "");
        int contextLines = positiveInt(options, "contextLines", DEFAULT_CONTEXT_LINES);
        int maxMatches = positiveInt(options, "maxMatches", DEFAULT_MAX_MATCHES);

        StringBuilder result = new StringBuilder();
        result.append("FILE_READ_RESULT\nPATH: ").append(target)
                .append("\nSEARCH: ").append(needle)
                .append("\nMATCHES:\n");

        List<String> previousLines = new ArrayList<String>();
        int matches = 0;
        int lineNumber = 0;
        try (BufferedReader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && matches < maxMatches) {
                lineNumber++;
                if (!line.contains(needle)) {
                    rememberPreviousLine(previousLines, line, contextLines);
                    continue;
                }

                matches++;
                result.append("-- match ").append(matches).append(", line ").append(lineNumber).append(" --\n");
                int previousStartLine = lineNumber - previousLines.size();
                for (int i = 0; i < previousLines.size(); i++) {
                    result.append(previousStartLine + i).append(": ").append(previousLines.get(i)).append('\n');
                }
                result.append(lineNumber).append(": ").append(line).append('\n');
                lineNumber += appendFollowingContext(reader, result, lineNumber, contextLines);
                previousLines.clear();
            }
        }

        if (matches == 0) {
            result.append("<no matches>\n");
        }
        return result.toString();
    }

    private void rememberPreviousLine(List<String> previousLines, String line, int contextLines) {
        if (contextLines <= 0) {
            return;
        }

        previousLines.add(line);
        while (previousLines.size() > contextLines) {
            previousLines.remove(0);
        }
    }

    private int appendFollowingContext(BufferedReader reader,
                                        StringBuilder result,
                                        int matchLine,
                                        int contextLines) throws IOException {
        int emitted = 0;
        for (int i = 1; i <= contextLines; i++) {
            String line = reader.readLine();
            if (line == null) {
                return emitted;
            }
            result.append(matchLine + i).append(": ").append(line).append('\n');
            emitted++;
        }
        return emitted;
    }

    private void writeLines(BufferedWriter writer, List<String> lines) throws IOException {
        for (String line : lines) {
            writer.write(line);
            writer.newLine();
        }
    }

    private Path resolvePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path is empty");
        }

        Path candidate = Paths.get(path.trim());
        if (!candidate.isAbsolute()) {
            candidate = workspace.resolve(candidate);
        }
        return candidate.toAbsolutePath().normalize();
    }

    private JsonNode readInstruction(String json) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("content JSON is empty");
        }
        return mapper.readTree(json);
    }

    private JsonNode readOptionalInstruction(String json) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            return mapper.createObjectNode();
        }
        return mapper.readTree(json);
    }

    private int positiveInt(JsonNode node, String field, int defaultValue) {
        int value = node.has(field) ? node.get(field).asInt(defaultValue) : defaultValue;
        return Math.max(1, value);
    }

    private String text(JsonNode node, String field, String defaultValue) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : defaultValue;
    }

    private List<String> splitLines(String value) {
        List<String> result = new ArrayList<String>();
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalized.split("\n", -1);
        for (String part : parts) {
            result.add(part);
        }
        return result;
    }

    private String quoteRegex(String value) {
        return java.util.regex.Pattern.quote(value);
    }

    private String quoteReplacement(String value) {
        return java.util.regex.Matcher.quoteReplacement(value);
    }

    private String error(String operation, String path, Exception e) {
        String error = e.getMessage() == null ? e.toString() : e.getMessage();
        return "FILE_OPERATION_ERROR\n" + operation + "\nPATH: " + path + "\nERROR: " + error;
    }
}
