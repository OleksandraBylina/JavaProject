// file: src/main/java/server/time/ConfigService.java
package server.time;

import java.time.*;

public final class ConfigService {
    private ConfigService(){}

    public static String timezone() { return "Europe/Kyiv"; }

    public static Instant submitFrom() { return Instant.parse("2025-11-01T00:00:00Z"); }
    public static Instant submitTo()   { return Instant.parse("2025-11-10T23:59:59Z"); }

    // <<< заменили даты окна рецензирования для теста сейчас >>>
    public static Instant reviewFrom() { return Instant.parse("2025-10-01T00:00:00Z"); }
    public static Instant reviewTo()   { return Instant.parse("2025-12-31T23:59:59Z"); }

    public static Instant resultsAt()  { return Instant.parse("2025-11-22T12:00:00Z"); }

    public static int minChars() { return 2000; }
    public static int maxChars() { return 30000; }

    public static boolean isSubmitOpenNow() {
        var now = Instant.now();
        return !now.isBefore(submitFrom()) && !now.isAfter(submitTo());
    }
}
