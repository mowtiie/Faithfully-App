package com.mowtiie.faithfully.data;

import androidx.annotation.Nullable;

/**
 * An entry in the Apps showcase, the same fields as the website's `apps` collection. Built by hand
 * from the Firestore document (see AppsActivity), never through toObject, so it needs no ProGuard rule.
 * {@code id} is null for the built-in list, which has not been saved to Firestore.
 */
public class AppEntry {

    @Nullable private final String id;
    private final String name;
    private final String tagline;
    private final String description;
    private final String version;
    private final String icon;
    private final String iconImage;
    private final String downloadUrl;
    private final String sourceUrl;
    private final long order;

    public AppEntry(@Nullable String id, @Nullable String name, @Nullable String tagline, @Nullable String description,
                    @Nullable String version, @Nullable String icon, @Nullable String iconImage,
                    @Nullable String downloadUrl, @Nullable String sourceUrl, long order) {
        this.id = id;
        this.name = orEmpty(name);
        this.tagline = orEmpty(tagline);
        this.description = orEmpty(description);
        this.version = orEmpty(version);
        this.icon = orEmpty(icon);
        this.iconImage = orEmpty(iconImage);
        this.downloadUrl = orEmpty(downloadUrl);
        this.sourceUrl = orEmpty(sourceUrl);
        this.order = order;
    }

    private static String orEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    @Nullable
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTagline() {
        return tagline;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }

    public String getIcon() {
        return icon;
    }

    public String getIconImage() {
        return iconImage;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public long getOrder() {
        return order;
    }
}
