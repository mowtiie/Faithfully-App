package com.mowtiie.faithfully.helper;

import java.util.Calendar;
import java.util.Date;

/**
 * Pure logic for the home screen, mirroring the website's js/home.js:
 * built-in sample text, overlaid by settings/home only for signed-in users.
 * No Android types, so it can be unit-tested on the JVM.
 */
public final class HomeContentResolver {

    public static final long DAY_MS = 1000L * 60 * 60 * 24;

    private HomeContentResolver() {}

    public static final class Text {
        public final String title, subtitle, countdownLabel, countdownFinish, anniversaryLabel, anniversaryUnit;

        public Text(String title, String subtitle, String countdownLabel,
                    String countdownFinish, String anniversaryLabel, String anniversaryUnit) {
            this.title = title;
            this.subtitle = subtitle;
            this.countdownLabel = countdownLabel;
            this.countdownFinish = countdownFinish;
            this.anniversaryLabel = anniversaryLabel;
            this.anniversaryUnit = anniversaryUnit;
        }

        /** Same sample text as the website, used only if mock_data.json cannot be read. */
        public static Text fallback() {
            return new Text(
                    "To Someone Special.",
                    "A quiet place made just for you — letters, memories, and countdowns for the moments ahead. 🌻",
                    "Counting down to something wonderful 🎂",
                    "Something wonderful is here! 🎉",
                    "Days on this journey 💛",
                    "days so far");
        }
    }

    /** Raw settings/home values; any field may be null. */
    public static final class Settings {
        public String title, subtitle, countdownLabel, countdownFinish, anniversaryLabel, anniversaryUnit;
        public Date countdownTarget, anniversaryStart;
    }

    public static final class Content {
        public final Text text;
        public final Date countdownTarget;
        /** Null when there is nothing usable to count from (shown as "--"). */
        public final Date anniversaryStart;

        Content(Text text, Date countdownTarget, Date anniversaryStart) {
            this.text = text;
            this.countdownTarget = countdownTarget;
            this.anniversaryStart = anniversaryStart;
        }
    }

    public static final class CountdownParts {
        public final long days, hours, minutes, seconds;

        CountdownParts(long days, long hours, long minutes, long seconds) {
            this.days = days;
            this.hours = hours;
            this.minutes = minutes;
            this.seconds = seconds;
        }
    }

    /**
     * @param signedIn   true for ADMIN/READER; false (GUEST) ignores {@code settings} entirely
     * @param settings   the settings/home document, or null if it does not exist or failed to load
     * @param guestStart when a guest first opened the app, used as the counter start for guests
     */
    public static Content resolve(Text defaults, boolean signedIn, Settings settings, Date guestStart, Date now) {
        Settings s = signedIn ? settings : null;

        Text text = new Text(
                pick(s == null ? null : s.title, defaults.title),
                pick(s == null ? null : s.subtitle, defaults.subtitle),
                pick(s == null ? null : s.countdownLabel, defaults.countdownLabel),
                pick(s == null ? null : s.countdownFinish, defaults.countdownFinish),
                pick(s == null ? null : s.anniversaryLabel, defaults.anniversaryLabel),
                pick(s == null ? null : s.anniversaryUnit, defaults.anniversaryUnit));

        Date target = s != null && s.countdownTarget != null ? s.countdownTarget : defaultCountdownTarget(now);
        Date start = signedIn ? (s == null ? null : s.anniversaryStart) : guestStart;

        return new Content(text, target, start);
    }

    private static String pick(String value, String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    /** Dec 31 of next year at 00:00 local time, as on the website. */
    public static Date defaultCountdownTarget(Date now) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.set(cal.get(Calendar.YEAR) + 1, Calendar.DECEMBER, 31, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /** Null once the target has passed or been reached. */
    public static CountdownParts countdownParts(Date target, Date now) {
        long diff = target.getTime() - now.getTime();
        if (diff <= 0) return null;

        long days    = diff / DAY_MS;
        long hours   = (diff % DAY_MS) / (1000L * 60 * 60);
        long minutes = (diff % (1000L * 60 * 60)) / (1000L * 60);
        long seconds = (diff % (1000L * 60)) / 1000L;
        return new CountdownParts(days, hours, minutes, seconds);
    }

    /** Whole days since {@code start}, never negative; -1 if there is no start. */
    public static long daysSince(Date start, Date now) {
        if (start == null) return -1;
        long diff = now.getTime() - start.getTime();
        return diff < 0 ? 0 : diff / DAY_MS;
    }

    /** The website splits on {@code <br>}, {@code <br/>} and newlines and renders each line as plain text. */
    public static String[] subtitleLines(String subtitle) {
        String[] raw = subtitle.split("(?i)<br\\s*/?>|\\n", -1);
        String[] lines = new String[raw.length];
        for (int i = 0; i < raw.length; i++) lines[i] = raw[i].trim();
        return lines;
    }
}
