// file: src/main/java/server/time/ConfigService.java
package server.time;

import java.time.*;

public final class ConfigService {
    private ConfigService(){}

    public static String timezone() { return "Europe/Kyiv"; }

    public static Instant submitFrom() { return Instant.parse("2025-11-20T00:00:00Z"); }
    public static Instant submitTo()   { return Instant.parse("2025-12-05T23:59:59Z"); }


    public static Instant reviewFrom() { return Instant.parse("2025-11-24T00:00:00Z"); }
    public static Instant reviewTo()   { return Instant.parse("2025-12-10T23:59:59Z"); }

    public static Instant resultsAt()  { return Instant.parse("2025-12-25T12:00:00Z"); }

    public static String expectedMailSubject() { return "Literature Contest"; }

    public static int minChars() { return 2000; }
    public static int maxChars() { return 30000; }
    public static int requiredReviewsPerClient() { return 3; }

    public static boolean isSubmitOpenNow() {
        var now = Instant.now();
        return !now.isBefore(submitFrom()) && !now.isAfter(submitTo());
    }
}
