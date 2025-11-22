package server.handlers;

import server.HttpRequest;
import server.HttpResponses;
import server.time.ConfigService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * PUT /submission  - обновление/замена оповідання клиента
 *   Headers: X-Client-Id, Content-Type (text/plain | application/vnd.openxmlformats-officedocument.wordprocessingml.document)
 *   Body:    bytes текста (txt) или docx
 *
 * PUT /reviews    - загрузка оценок в CSV
 *   Headers: X-Client-Id, Content-Type: text/csv
 *   Body:    строки вида: storyId,score\n ...
 */
public class PutHandler implements Handler {

    private final Path base = Paths.get("storage");

    @Override
    public void handle(HttpRequest req, OutputStream out) throws IOException {
        if (!"PUT".equalsIgnoreCase(req.method)) {
            HttpResponses.text(out, 405, "Method Not Allowed");
            return;
        }

        String path = req.path;
        if ("/submission".equals(path)) {
            putSubmission(req, out);
        } else if ("/reviews".equals(path)) {
            putReviews(req, out);
        } else {
            HttpResponses.text(out, 404, "Not Found");
        }
    }

    /* =================== PUT /submission =================== */

    private void putSubmission(HttpRequest req, OutputStream out) throws IOException {
        String clientId = header(req, "x-client-id");
        if (clientId == null || clientId.isBlank()) {
            HttpResponses.json(out, 401, "{\"error\":\"missing X-Client-Id\"}");
            return;
        }

        if (!ConfigService.isSubmitOpenNow()) {
            HttpResponses.json(out, 403, "{\"error\":\"submission window closed\"}");
            return;
        }

        byte[] body = req.body;
        if (body == null || body.length == 0) {
            HttpResponses.json(out, 422, "{\"error\":\"empty body\"}");
            return;
        }

        String ct = header(req, "content-type");
        if (ct == null) ct = "";
        ct = ct.toLowerCase();

        String ext;
        if (ct.startsWith("text/plain")) {
            ext = ".txt";
        } else if (ct.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            ext = ".docx";
        } else {
            HttpResponses.json(out, 415, "{\"error\":\"Content-Type must be text/plain or docx\"}");
            return;
        }

        // Сохраняем как storage/submissions/<clientId>/story.ext (перезапись разрешена)
        Path dir  = base.resolve("submissions").resolve(safe(clientId));
        Files.createDirectories(dir);
        Path file = dir.resolve("story" + ext);
        Files.write(file, body);

        HttpResponses.json(out, 201,
                ("{\"status\":\"replaced\",\"clientId\":\"%s\",\"file\":\"%s\"}")
                        .formatted(escape(clientId), file.getFileName()));
    }

    /* =================== PUT /reviews =================== */

    private void putReviews(HttpRequest req, OutputStream out) throws IOException {
        String clientId = header(req, "x-client-id");
        if (clientId == null || clientId.isBlank()) {
            HttpResponses.json(out, 401, "{\"error\":\"missing X-Client-Id\"}");
            return;
        }

        // стало — прямая проверка по reviewFrom()/reviewTo()
        var now = java.time.Instant.now();
        if ( now.isBefore(server.time.ConfigService.reviewFrom())
                || now.isAfter (server.time.ConfigService.reviewTo()) ) {
            HttpResponses.json(out, 403, "{\"error\":\"review window closed\"}");
            return;
        }


        String ct = header(req, "content-type");
        if (ct == null || !ct.toLowerCase().startsWith("text/csv")) {
            HttpResponses.json(out, 415, "{\"error\":\"Content-Type must be text/csv\"}");
            return;
        }

        String csv = req.bodyAsString();
        if (csv.isBlank()) {
            HttpResponses.json(out, 422, "{\"error\":\"CSV is empty\"}");
            return;
        }

        List<String> normalized = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        int lineNo = 0;
        for (String line : csv.split("\\R")) {
            lineNo++;
            if (line.isBlank()) continue;

            String[] p = line.split(",", -1);
            if (p.length != 2) {
                errors.add("line " + lineNo + ": expect storyId,score");
                continue;
            }
            String storyId = p[0].trim();
            String scoreStr = p[1].trim();

            int score;
            try {
                score = Integer.parseInt(scoreStr);
            } catch (NumberFormatException e) {
                errors.add("line " + lineNo + ": score is not integer");
                continue;
            }
            if (score < 1 || score > 10) {
                errors.add("line " + lineNo + ": score out of range 1..10");
                continue;
            }
            // Простейшая защита от самооценки: если storyId совпадает с clientId
            if (storyId.equalsIgnoreCase(clientId)) {
                errors.add("line " + lineNo + ": self-review is not allowed");
                continue;
            }
            normalized.add(storyId + "," + score);
        }

        // Сохраняем как storage/reviews/<clientId>.csv (перезапись)
        Path dir = base.resolve("reviews");
        Files.createDirectories(dir);
        Path file = dir.resolve(safe(clientId) + ".csv");
        Files.writeString(file, String.join("\n", normalized) + (normalized.isEmpty() ? "" : "\n"));

        String json = """
        {
          "status":"accepted",
          "clientId":"%s",
          "saved":"%d",
          "errors":"%d",
          "errorsSample":%s
        }
        """.formatted(
                escape(clientId),
                normalized.size(),
                errors.size(),
                toJsonArraySample(errors, 5)
        );

        HttpResponses.json(out, 201, json);
    }

    /* =================== helpers =================== */

    private static String header(HttpRequest r, String name) {
        return r.header(name);
    }

    private static String safe(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toJsonArraySample(List<String> list, int max) {
        StringBuilder sb = new StringBuilder("[");
        int n = Math.min(max, list.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(list.get(i))).append('"');
        }
        if (list.size() > max) {
            if (n > 0) sb.append(',');
            sb.append('"').append("... +" + (list.size() - max) + " more").append('"');
        }
        sb.append(']');
        return sb.toString();
    }
}
