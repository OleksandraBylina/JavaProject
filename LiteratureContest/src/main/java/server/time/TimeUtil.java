package server.time;

import java.time.*;

public final class TimeUtil {
    private TimeUtil(){}
    public static String format(Instant inst, ZoneId tz) {
        return inst.atZone(tz).toOffsetDateTime().toString(); // ISO-8601 с оффсетом
    }
}
