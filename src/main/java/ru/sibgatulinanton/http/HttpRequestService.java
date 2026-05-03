package ru.sibgatulinanton.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HttpRequestService {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 10000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 30000;
    private static final int DEFAULT_MAX_BODY_CHARS = 20000;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    public String execute(String url, String optionsJson) {
        HttpURLConnection connection = null;
        try {
            JsonNode options = readOptions(optionsJson);
            URL target = new URL(requireUrl(url));
            String protocol = target.getProtocol() == null ? "" : target.getProtocol().toLowerCase(Locale.ROOT);
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                return "HTTP_REQUEST_ERROR\nURL: " + url + "\nERROR: only http and https protocols are supported";
            }

            connection = (HttpURLConnection) target.openConnection();
            connection.setConnectTimeout(positiveInt(options, "connectTimeoutMillis", DEFAULT_CONNECT_TIMEOUT_MILLIS));
            connection.setReadTimeout(positiveInt(options, "readTimeoutMillis", DEFAULT_READ_TIMEOUT_MILLIS));
            connection.setInstanceFollowRedirects(booleanValue(options, "followRedirects", true));
            connection.setRequestMethod(method(options));
            applyHeaders(connection, options.get("headers"));
            writeBody(connection, text(options, "body", null));

            int status = connection.getResponseCode();
            String responseBody = readResponseBody(connection, status, positiveInt(options, "maxBodyChars", DEFAULT_MAX_BODY_CHARS));

            return "HTTP_REQUEST_RESULT\n"
                    + "URL: " + target + "\n"
                    + "METHOD: " + connection.getRequestMethod() + "\n"
                    + "STATUS: " + status + "\n"
                    + "HEADERS:\n" + headers(connection.getHeaderFields())
                    + "BODY:\n" + responseBody;
        } catch (Exception e) {
            return error(url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JsonNode readOptions(String json) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            return mapper.createObjectNode();
        }
        return mapper.readTree(json);
    }

    private String requireUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("url is empty");
        }
        return url.trim();
    }

    private String method(JsonNode options) {
        String method = text(options, "method", "GET").trim().toUpperCase(Locale.ROOT);
        if (method.isEmpty()) {
            return "GET";
        }
        return method;
    }

    private void applyHeaders(HttpURLConnection connection, JsonNode headers) {
        if (headers == null || !headers.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = headers.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            JsonNode value = field.getValue();
            if (name == null || name.trim().isEmpty() || value == null || value.isNull()) {
                continue;
            }
            if (value.isArray()) {
                for (JsonNode item : value) {
                    if (item != null && !item.isNull()) {
                        connection.addRequestProperty(name, item.asText(""));
                    }
                }
            } else {
                connection.setRequestProperty(name, value.asText(""));
            }
        }
    }

    private void writeBody(HttpURLConnection connection, String body) throws IOException {
        if (body == null) {
            return;
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
    }

    private String readResponseBody(HttpURLConnection connection, int status, int maxBodyChars) throws IOException {
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }

        byte[] bytes = readBytes(stream);
        String body = new String(bytes, StandardCharsets.UTF_8);
        if (body.length() <= maxBodyChars) {
            return body;
        }
        return body.substring(0, maxBodyChars) + "\n...TRUNCATED_TO_CHARS: " + maxBodyChars;
    }

    private byte[] readBytes(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String headers(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return "<none>\n";
        }

        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.append(entry.getKey()).append(": ");
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                result.append('\n');
                continue;
            }
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    result.append(", ");
                }
                result.append(values.get(i));
            }
            result.append('\n');
        }
        return result.toString();
    }

    private int positiveInt(JsonNode node, String field, int defaultValue) {
        int value = node != null && node.has(field) ? node.get(field).asInt(defaultValue) : defaultValue;
        return Math.max(1, value);
    }

    private boolean booleanValue(JsonNode node, String field, boolean defaultValue) {
        return node != null && node.has(field) ? node.get(field).asBoolean(defaultValue) : defaultValue;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : defaultValue;
    }

    private String error(String url, Exception e) {
        String error = e.getMessage() == null ? e.toString() : e.getMessage();
        return "HTTP_REQUEST_ERROR\nURL: " + url + "\nERROR: " + error;
    }
}
