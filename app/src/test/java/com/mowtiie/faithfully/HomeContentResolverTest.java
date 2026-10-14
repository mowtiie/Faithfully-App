package com.mowtiie.faithfully;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.mowtiie.faithfully.helper.HomeContentResolver;
import com.mowtiie.faithfully.helper.HomeContentResolver.Content;
import com.mowtiie.faithfully.helper.HomeContentResolver.CountdownParts;
import com.mowtiie.faithfully.helper.HomeContentResolver.Settings;
import com.mowtiie.faithfully.helper.HomeContentResolver.Text;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

public class HomeContentResolverTest {

    private static final Text DEFAULTS = Text.fallback();
    private static final Date NOW = new Date(1_700_000_000_000L);

    private static Settings settings() {
        Settings s = new Settings();
        s.title = "Real title";
        s.subtitle = "";
        s.anniversaryStart = new Date(NOW.getTime() - 10 * HomeContentResolver.DAY_MS - 5000);
        s.countdownTarget = new Date(NOW.getTime() + 90_061_000L);
        return s;
    }

    @Test
    public void signedInOverlaysNonEmptyFieldsOnly() {
        Content c = HomeContentResolver.resolve(DEFAULTS, true, settings(), null, NOW);
        assertEquals("Real title", c.text.title);
        assertEquals(DEFAULTS.subtitle, c.text.subtitle);
        assertEquals(DEFAULTS.countdownLabel, c.text.countdownLabel);
    }

    @Test
    public void guestIgnoresSettingsEntirely() {
        Date guestStart = new Date(NOW.getTime() - 3 * HomeContentResolver.DAY_MS);
        Content c = HomeContentResolver.resolve(DEFAULTS, false, settings(), guestStart, NOW);
        assertEquals(DEFAULTS.title, c.text.title);
        assertEquals(guestStart, c.anniversaryStart);
        assertEquals(HomeContentResolver.defaultCountdownTarget(NOW), c.countdownTarget);
    }

    @Test
    public void signedInWithoutDocumentShowsDefaultsAndNoCounter() {
        Content c = HomeContentResolver.resolve(DEFAULTS, true, null, null, NOW);
        assertEquals(DEFAULTS.title, c.text.title);
        assertNull(c.anniversaryStart);
        assertEquals(-1, HomeContentResolver.daysSince(c.anniversaryStart, NOW));
    }

    @Test
    public void defaultTargetIsDec31OfNextYearAtMidnight() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(HomeContentResolver.defaultCountdownTarget(NOW));
        Calendar now = Calendar.getInstance();
        now.setTime(NOW);
        assertEquals(now.get(Calendar.YEAR) + 1, cal.get(Calendar.YEAR));
        assertEquals(Calendar.DECEMBER, cal.get(Calendar.MONTH));
        assertEquals(31, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, cal.get(Calendar.MINUTE));
    }

    @Test
    public void countdownSplitsIntoDaysHoursMinutesSeconds() {
        // 1 day, 1 hour, 1 minute, 1 second
        CountdownParts p = HomeContentResolver.countdownParts(new Date(NOW.getTime() + 90_061_000L), NOW);
        assertNotNull(p);
        assertEquals(1, p.days);
        assertEquals(1, p.hours);
        assertEquals(1, p.minutes);
        assertEquals(1, p.seconds);
    }

    @Test
    public void countdownIsNullOnceReached() {
        assertNull(HomeContentResolver.countdownParts(NOW, NOW));
        assertNull(HomeContentResolver.countdownParts(new Date(NOW.getTime() - 1), NOW));
    }

    @Test
    public void daysSinceFloorsAndNeverGoesNegative() {
        Date tenDaysAgo = new Date(NOW.getTime() - 10 * HomeContentResolver.DAY_MS - 5000);
        assertEquals(10, HomeContentResolver.daysSince(tenDaysAgo, NOW));
        assertEquals(0, HomeContentResolver.daysSince(new Date(NOW.getTime() + 5000), NOW));
    }

    @Test
    public void subtitleSplitsOnBrAndNewlines() {
        assertArrayEquals(new String[]{"a", "b", "c", "d"},
                HomeContentResolver.subtitleLines("a<br>b <BR/> c\nd"));
        assertArrayEquals(new String[]{"one line"}, HomeContentResolver.subtitleLines("one line"));
    }
}
