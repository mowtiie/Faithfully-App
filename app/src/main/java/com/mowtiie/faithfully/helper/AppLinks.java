package com.mowtiie.faithfully.helper;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns the links stored with an app (the website writes paths like "./apps/Faithful.apk") into
 * addresses the phone can open. Only http and https links are allowed, like the website's safeUrl.
 * Pure Java, unit-tested in AppLinksTest.
 */
public final class AppLinks {

    public static final String SITE = "https://alliyannah.love/";

    private static final Pattern ANY_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    private AppLinks() {}

    /** The full link, or null when it is empty or not something the app should open. */
    @Nullable
    public static String resolve(@Nullable String link) {
        if (link == null) return null;

        String value = link.trim();
        if (value.isEmpty()) return null;

        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://") || lower.startsWith("http://")) return value;
        if (ANY_SCHEME.matcher(value).find() || value.startsWith("//")) return null;

        String path = value;
        while (path.startsWith("./")) path = path.substring(2);
        while (path.startsWith("/")) path = path.substring(1);
        if (path.isEmpty() || path.contains("..")) return null;

        return SITE + path;
    }
}
