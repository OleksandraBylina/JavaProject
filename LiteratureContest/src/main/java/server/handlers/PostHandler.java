// file: src/main/java/server/handlers/PostHandler.java
package server.handlers;

import server.HttpRequest;
import server.HttpResponses;
import server.logic.ContestService;
import server.time.ConfigService;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;

public class PostHandler implements Handler {

    private final ContestService contest;

    public PostHandler() throws IOException {
        this.contest = new ContestService();
    }

    @Override
    public void handle(HttpRequest req, OutputStream out) throws IOException {
        if ("/submit".equals(req.path)) {
            submit(req, out);
        } else {
            HttpResponses.text(out, 404, "Not Found");
        }
    }

    private void submit(HttpRequest req, OutputStream out) throws IOException {
        // 1) Идентификация клиента: ожидаем заголовок X-Client-Id (stateless)
        String clientId = req.header("x-client-id");
        if (clientId == null || clientId.isBlank()) {
            HttpResponses.json(out, 401, "{\"error\":\"missing X-Client-Id\"}");
            return;
        }

        // 2) Окно приёма
        if (!ConfigService.isSubmitOpenNow()) {
            HttpResponses.json(out, 403, "{\"error\":\"submission window closed\"}");
            return;
        }

        // 3) Тип содержимого
        String ct = req.header("content-type");
        if (ct == null || !ct.toLowerCase().startsWith("application/json")) {
            HttpResponses.json(out, 415, "{\"error\":\"Content-Type must be application/json\"}");
            return;
        }

        // 4) Разбор JSON (минимально: ищем "title" и "text")
        String body = req.bodyAsString().trim();
        String title = extractJsonString(body, "title");
        String text  = extractJsonString(body, "text");
        if (title == null || title.isBlank() || text == null || text.isBlank()) {
            HttpResponses.json(out, 422, "{\"error\":\"title and text are required\"}");
            return;
        }

        // 5) Валидация длины
        int chars = text.codePointCount(0, text.length());
        int A = ConfigService.minChars(), B = ConfigService.maxChars();
        if (chars <= A || chars >= B) {
            HttpResponses.json(out, 422,
                    ("{\"error\":\"length must be between %d and %d, got %d\"}")
                            .formatted(A, B, chars));
            return;
        }

        // 6) Сохраняем текстовую версию и фиксируем регистрацию
        var submission = contest.registerTextSubmission(clientId, title, text);
        String json = """
            {"status":"accepted","submissionId":"%s","title":"%s","receivedAt":"%s"}
            """.formatted(submission.submissionId(), escape(submission.title()), Instant.ofEpochMilli(submission.receivedAtUtc()));

        HttpResponses.json(out, 201, json);
    }

    // Очень простой парсер "key":"value" (без экранирования внутри value, на первое время хватит)
    private String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
