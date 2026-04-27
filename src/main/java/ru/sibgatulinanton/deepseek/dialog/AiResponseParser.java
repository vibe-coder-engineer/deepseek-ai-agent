package ru.sibgatulinanton.deepseek.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AiResponseParser {

    private static final String JSON_FENCE = "```json";
    private static final String MARKDOWN_FENCE = "```";
    private static final String COPY_LABEL_EN = "copy";
    private static final String DOWNLOAD_LABEL_EN = "download";
    private static final String COPY_LABEL_RU = "копировать";
    private static final String DOWNLOAD_LABEL_RU = "скачать";

    private final ObjectMapper mapper = JsonMapper.builder().build();

    public List<AiOperation> parseOperations(String rawResponse) {
        String response = cleanResponse(rawResponse);
        ObjectNode root = extractJsonObject(response);
        if (root == null) {
            return null;
        }

        JsonNode operationsNode = root.get("operations");
        if (operationsNode != null && operationsNode.isArray()) {
            List<AiOperation> operations = new ArrayList<AiOperation>();
            for (JsonNode operationNode : operationsNode) {
                if (operationNode != null && operationNode.isObject()) {
                    operations.add(toOperation(operationNode));
                }
            }
            return operations;
        }

        if (root.has("type")) {
            List<AiOperation> operations = new ArrayList<AiOperation>();
            operations.add(toOperation(root));
            return operations;
        }

        return null;
    }

    public String cleanResponse(String rawResponse) {
        if (rawResponse == null) {
            return "";
        }

        return rawResponse
                .replace(JSON_FENCE, "")
                .replace(MARKDOWN_FENCE, "")
                .replaceAll("(?im)^\\s*json\\s*$", "")
                .replaceAll(labelLinePattern(COPY_LABEL_EN), "")
                .replaceAll(labelLinePattern(DOWNLOAD_LABEL_EN), "")
                .replaceAll(labelLinePattern(COPY_LABEL_RU), "")
                .replaceAll(labelLinePattern(DOWNLOAD_LABEL_RU), "")
                .trim();
    }

    private String labelLinePattern(String label) {
        return "(?im)^\\s*" + label + "\\s*$";
    }

    private ObjectNode extractJsonObject(String text) {
        if (text == null) {
            return null;
        }

        String trimmed = text.trim();
        ObjectNode parsed = readObject(trimmed);
        if (parsed != null) {
            return parsed;
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return null;
        }

        return readObject(trimmed.substring(firstBrace, lastBrace + 1).trim());
    }

    private ObjectNode readObject(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return node != null && node.isObject() ? (ObjectNode) node : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private AiOperation toOperation(JsonNode operationNode) {
        return new AiOperation(
                textValue(operationNode.get("type")),
                textValue(operationNode.get("data")),
                contentValue(operationNode.get("content"))
        );
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private String contentValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }
}
