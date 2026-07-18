package nl.roy.raspicardashboard;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/** Small offline sunrise/sunset calculator suitable for automatic dashboard dimming. */
public final class SunCalculator {
    private SunCalculator() { }

    public static boolean isNight(Date date, double latitude, double longitude, int offsetMinutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int day = calendar.get(Calendar.DAY_OF_YEAR);
        int currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        int timezoneOffset = TimeZone.getDefault().getOffset(date.getTime()) / 60_000;

        double gamma = 2.0 * Math.PI / 365.0 * (day - 1);
        double equationOfTime = 229.18 * (0.000075
                + 0.001868 * Math.cos(gamma)
                - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma)
                - 0.040849 * Math.sin(2 * gamma));
        double declination = 0.006918
                - 0.399912 * Math.cos(gamma)
                + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma)
                + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma)
                + 0.00148 * Math.sin(3 * gamma);

        double latRad = Math.toRadians(Math.max(-89.5, Math.min(89.5, latitude)));
        double zenith = Math.toRadians(90.833);
        double cosHourAngle = (Math.cos(zenith) / (Math.cos(latRad) * Math.cos(declination)))
                - Math.tan(latRad) * Math.tan(declination);

        // Polar day/night fallback. It is not expected in the Netherlands, but avoids NaN.
        if (cosHourAngle > 1.0) return true;
        if (cosHourAngle < -1.0) return false;

        double hourAngleDegrees = Math.toDegrees(Math.acos(cosHourAngle));
        double solarNoon = 720.0 - (4.0 * longitude) - equationOfTime + timezoneOffset;
        int sunrise = normalize((int) Math.round(solarNoon - 4.0 * hourAngleDegrees + offsetMinutes));
        int sunset = normalize((int) Math.round(solarNoon + 4.0 * hourAngleDegrees + offsetMinutes));

        if (sunrise <= sunset) return currentMinutes < sunrise || currentMinutes >= sunset;
        return currentMinutes >= sunset && currentMinutes < sunrise;
    }

    private static int normalize(int minutes) {
        int value = minutes % 1440;
        return value < 0 ? value + 1440 : value;
    }
}
