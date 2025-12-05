package server.handlers;

import server.HttpRequest;
import server.HttpResponses;
import server.logic.ContestService;
import server.time.ConfigService;
import server.time.TimeUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;

public class GetHandler implements Handler {

    private final ContestService contest;

    public GetHandler() throws IOException {
        this.contest = new ContestService();
    }

    @Override
    public void handle(HttpRequest req, OutputStream out) throws IOException {
        if ("/status".equals(req.path)) {
            status(out);
        } else if ("/assignments".equals(req.path)) {
            assignments(req, out);
        } else if ("/assignments.xlsx".equals(req.path)) {
            assignmentsWorkbook(req, out);
        } else if ("/assignments.zip".equals(req.path)) {
            assignmentsArchive(req, out);
        } else if ("/results".equals(req.path)) {
            results(out);
        } else {
            HttpResponses.text(out, 404, "Not Found");
        }
    }

    private void status(OutputStream out) throws IOException {
        var tz = ZoneId.of(ConfigService.timezone());
        Instant now   = Instant.now();

        var submitFrom = ConfigService.submitFrom();
        var submitTo   = ConfigService.submitTo();
        var reviewFrom = ConfigService.reviewFrom();
        var reviewTo   = ConfigService.reviewTo();
        var resultsAt  = ConfigService.resultsAt();

        boolean isSubmitOpen    = !now.isBefore(submitFrom) && !now.isAfter(submitTo);
        boolean isReviewOpen    = !now.isBefore(reviewFrom) && !now.isAfter(reviewTo);
        boolean areResultsReady = !now.isBefore(resultsAt);

        String json = """
        {
          "serverTime":  "%s",
          "timezone":    "%s",
          "submitFrom":  "%s",
          "submitTo":    "%s",
          "reviewFrom":  "%s",
          "reviewTo":    "%s",
          "resultsAt":   "%s",
          "isSubmitOpen": %s,
          "isReviewOpen": %s,
          "areResultsReady": %s
        }
        """.formatted(
                TimeUtil.format(now, tz), tz.getId(),
                TimeUtil.format(submitFrom, tz), TimeUtil.format(submitTo,   tz),
                TimeUtil.format(reviewFrom, tz), TimeUtil.format(reviewTo,   tz),
                TimeUtil.format(resultsAt,  tz),
                isSubmitOpen, isReviewOpen, areResultsReady
        );

        HttpResponses.json(out, 200, json);
    }

    private void assignments(HttpRequest req, OutputStream out) throws IOException {
        String clientId = req.header("x-client-id");
        if (clientId == null || clientId.isBlank()) {
            HttpResponses.json(out, 401, "{\"error\":\"missing X-Client-Id\"}");
            return;
        }

        var assignment = contest.assignmentsFor(clientId);
        String json = """
        {"clientId":"%s","stories":%s}
        """.formatted(escape(clientId), toJsonArray(assignment.submissionIds()));
        HttpResponses.json(out, 200, json);
    }

    private void assignmentsWorkbook(HttpRequest req, OutputStream out) throws IOException {
        String clientId = req.header("x-client-id");
        if (clientId == null || clientId.isBlank()) {
            HttpResponses.json(out, 401, "{\"error\":\"missing X-Client-Id\"}");
            return;
        }
        byte[] workbook = contest.assignmentsWorkbook(clientId);
        out.write(("HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n" +
                "Content-Length: " + workbook.length + "\r\n" +
                "Content-Disposition: attachment; filename=assignments.xlsx\r\n\r\n").getBytes());
        out.write(workbook);
    }

    private void assignmentsArchive(HttpRequest req, OutputStream out) throws IOException {
        String clientId = req.header("x-client-id");
        if (clientId == null || clientId.isBlank()) {
            HttpResponses.json(out, 401, "{\"error\":\"missing X-Client-Id\"}");
            return;
        }
        byte[] zip = contest.assignmentsArchive(clientId);
        out.write(("HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/zip\r\n" +
                "Content-Length: " + zip.length + "\r\n" +
                "Content-Disposition: attachment; filename=assignments.zip\r\n\r\n").getBytes());
        out.write(zip);
    }

    private void results(OutputStream out) throws IOException {
        if (Instant.now().isBefore(ConfigService.resultsAt())) {
            HttpResponses.json(out, 403, "{\"error\":\"results are not ready yet\"}");
            return;
        }
        var results = contest.generateResults();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"generatedAt\":").append(results.generatedAtUtc()).append(",\"disqualified\":[");
        for (int i = 0; i < results.disqualified().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('\"').append(escape(results.disqualified().get(i))).append('\"');
        }
        sb.append("],\"protocol\":{")
                .append("\"totalSubmissions\":").append(results.protocol().totalSubmissions()).append(',')
                .append("\"totalReviewers\":").append(results.protocol().totalReviewers()).append(',')
                .append("\"requiredReviews\":").append(results.protocol().requiredReviews()).append(',')
                .append("\"submittedReviews\":").append(results.protocol().submittedReviews()).append(',')
                .append("\"insufficientStories\":").append(toJsonArray(results.protocol().insufficientStories())).append(',')
                .append("\"disqualifiedAuthors\":").append(toJsonArray(results.protocol().disqualifiedAuthors()))
                .append("},\"items\":[");
        for (int i = 0; i < results.items().size(); i++) {
            var it = results.items().get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"storyId\":\"").append(escape(it.storyId())).append("\",")
                    .append("\"title\":\"").append(escape(it.title())).append("\",")
                    .append("\"avg\":").append(String.format(java.util.Locale.US, "%.2f", it.avgScore())).append(',')
                    .append("\"count\":").append(it.reviewsCount()).append(',')
                    .append("\"insufficientReviews\":").append(it.insufficientReviews()).append('}');
        }
        sb.append("]}");
        HttpResponses.json(out, 200, sb.toString());
    }

    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String toJsonArray(java.util.List<String> ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('\"').append(escape(ids.get(i))).append('\"');
        }
        sb.append(']');
        return sb.toString();
    }
}
