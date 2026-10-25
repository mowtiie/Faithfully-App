package com.mowtiie.faithfully.helper;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/** Keys, file types and clock text for voice clips. Pure Java, unit-tested in VoiceFormatTest. */
public final class VoiceFormat {

    public static final int MAX_SECONDS = 90;
    public static final long MAX_BYTES = 2L * 1024 * 1024;

    // Same key shape the Worker accepts for voice clips.
    private static final Pattern KEY = Pattern.compile(
            "^voice/[A-Za-z0-9]{1,40}/[A-Za-z0-9][A-Za-z0-9._-]{0,120}\\.(webm|m4a|mp4|ogg)$");

    private VoiceFormat() {}

    public static String formatClock(double seconds) {
        long total = Math.max(0, (long) Math.floor(seconds));
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }

    public static boolean isVoiceKey(@Nullable String key) {
        return key != null && KEY.matcher(key).matches();
    }

    public static String newKey(String uid, String extension, long nowMillis, String suffix) {
        return "voice/" + uid + "/" + nowMillis + "_" + suffix + "." + extension;
    }

    /** The extension the Worker expects for a recorder MIME type, or null when it is not a supported one. */
    @Nullable
    public static String extensionForMime(@Nullable String mimeType) {
        if (mimeType == null) return null;
        switch (baseType(mimeType)) {
            case "audio/webm": return "webm";
            case "audio/mp4":  return "m4a";
            case "audio/ogg":  return "ogg";
            default:           return null;
        }
    }

    private static String baseType(String mimeType) {
        int semi = mimeType.indexOf(';');
        return (semi < 0 ? mimeType : mimeType.substring(0, semi)).trim().toLowerCase(Locale.ROOT);
    }
}
