package server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HttpResponses {
    public static void json(OutputStream out, int code, String json) throws IOException {
        json(out, code, json, Map.of());
    }
    public static void json(OutputStream out, int code, String json, Map<String,String> extra) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder()
                .append("HTTP/1.1 ").append(code).append(" ").append(reason(code)).append("\r\n")
                .append("Content-Type: application/json; charset=utf-8\r\n")
                .append("Content-Length: ").append(body.length).append("\r\n")
                .append("Connection: close\r\n");
        for (var e: extra.entrySet()) sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }
    public static void text(OutputStream out, int code, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 "+code+" "+reason(code)+"\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: "+body.length+"\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }
    private static String reason(int c) {
        return switch (c) {
            case 200 -> "OK"; case 201 -> "Created"; case 202 -> "Accepted"; case 204 -> "No Content";
            case 400 -> "Bad Request"; case 401 -> "Unauthorized"; case 403 -> "Forbidden"; case 404 -> "Not Found";
            case 409 -> "Conflict"; case 415 -> "Unsupported Media Type"; case 422 -> "Unprocessable Entity";
            default -> "Status";
        };
    }
}
