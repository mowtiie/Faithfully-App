package com.mowtiie.faithfully;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mowtiie.faithfully.helper.VoiceFormat;

import org.junit.Test;

public class VoiceFormatTest {

    @Test
    public void formatClock_pads_seconds_and_rolls_into_minutes() {
        assertEquals("0:00", VoiceFormat.formatClock(0));
        assertEquals("0:09", VoiceFormat.formatClock(9.9));
        assertEquals("1:05", VoiceFormat.formatClock(65));
        assertEquals("1:30", VoiceFormat.formatClock(VoiceFormat.MAX_SECONDS));
    }

    @Test
    public void formatClock_never_goes_negative() {
        assertEquals("0:00", VoiceFormat.formatClock(-4));
    }

    @Test
    public void newKey_builds_a_key_the_worker_accepts() {
        String key = VoiceFormat.newKey("371O09ErYFShTBzNDdInf40FUE23", "m4a", 1792943740833L, "ab12cd");
        assertEquals("voice/371O09ErYFShTBzNDdInf40FUE23/1792943740833_ab12cd.m4a", key);
        assertTrue(VoiceFormat.isVoiceKey(key));
    }

    @Test
    public void isVoiceKey_matches_the_workers_rules() {
        assertTrue(VoiceFormat.isVoiceKey("voice/ali/a.webm"));
        assertTrue(VoiceFormat.isVoiceKey("voice/ali/a.ogg"));
        assertTrue(VoiceFormat.isVoiceKey("voice/ali/a.mp4"));

        assertFalse(VoiceFormat.isVoiceKey(null));
        assertFalse(VoiceFormat.isVoiceKey("voice/a.webm"));
        assertFalse(VoiceFormat.isVoiceKey("voice/ali/a/b.webm"));
        assertFalse(VoiceFormat.isVoiceKey("voice/ali/a.mp3"));
        assertFalse(VoiceFormat.isVoiceKey("voice/ali/.webm"));
        assertFalse(VoiceFormat.isVoiceKey("voice/al-i/a.webm"));
        assertFalse(VoiceFormat.isVoiceKey("Voice/ali/a.webm"));
        assertFalse(VoiceFormat.isVoiceKey("gallery/a.png"));
    }

    @Test
    public void extensionForMime_ignores_codec_parameters() {
        assertEquals("m4a", VoiceFormat.extensionForMime("audio/mp4"));
        assertEquals("webm", VoiceFormat.extensionForMime("audio/webm;codecs=opus"));
        assertEquals("ogg", VoiceFormat.extensionForMime("Audio/OGG"));
        assertNull(VoiceFormat.extensionForMime("audio/mpeg"));
        assertNull(VoiceFormat.extensionForMime(null));
    }

    @Test
    public void mimeForKey_follows_the_extension() {
        assertEquals("audio/webm", VoiceFormat.mimeForKey("voice/ali/a.webm"));
        assertEquals("audio/ogg", VoiceFormat.mimeForKey("voice/ali/a.ogg"));
        assertEquals("audio/mp4", VoiceFormat.mimeForKey("voice/ali/a.m4a"));
        assertEquals("audio/mp4", VoiceFormat.mimeForKey("voice/ali/a.mp4"));
    }

    @Test
    public void extensionOfKey_is_lower_case() {
        assertEquals("m4a", VoiceFormat.extensionOfKey("voice/ali/a.M4A"));
        assertEquals("", VoiceFormat.extensionOfKey("voice/ali/noextension"));
    }
}
