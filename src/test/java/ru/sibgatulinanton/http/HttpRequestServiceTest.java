package ru.sibgatulinanton.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class HttpRequestServiceTest extends TestCase {

    private HttpServer server;
    private String baseUrl;

    @Override
    protected void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/get", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String response = "method=" + exchange.getRequestMethod()
                        + ";accept=" + exchange.getRequestHeaders().getFirst("Accept");
                write(exchange, 200, response);
            }
        });
        server.createContext("/post", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                write(exchange, 201, "body=" + readBody(exchange));
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    protected void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    public void testExecutesGetRequest() {
        String result = new HttpRequestService().execute(
                baseUrl + "/get",
                "{\"headers\":{\"Accept\":\"application/json\"}}"
        );

        assertTrue(result.contains("HTTP_REQUEST_RESULT"));
        assertTrue(result.contains("STATUS: 200"));
        assertTrue(result.contains("BODY:\nmethod=GET;accept=application/json"));
    }

    public void testExecutesPostRequestWithBody() {
        String result = new HttpRequestService().execute(
                baseUrl + "/post",
                "{\"method\":\"POST\",\"headers\":{\"Content-Type\":\"application/json\"},\"body\":\"{\\\"name\\\":\\\"demo\\\"}\"}"
        );

        assertTrue(result.contains("STATUS: 201"));
        assertTrue(result.contains("BODY:\nbody={\"name\":\"demo\"}"));
    }

    public void testRejectsUnsupportedProtocol() {
        String result = new HttpRequestService().execute("file:///tmp/test.txt", null);

        assertTrue(result.contains("HTTP_REQUEST_ERROR"));
        assertTrue(result.contains("only http and https protocols are supported"));
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
