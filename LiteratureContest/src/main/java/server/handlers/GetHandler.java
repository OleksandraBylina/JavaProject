package server.handlers;

import server.HttpRequest;
import server.HttpResponses;
import server.time.ConfigService;
import server.time.TimeUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;

public class GetHandler implements Handler {

    @Override
    public void handle(HttpRequest req, OutputStream out) throws IOException {
        if ("/status".equals(req.path)) {
            status(out);
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
}
