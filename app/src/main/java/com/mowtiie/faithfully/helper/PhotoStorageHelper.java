package com.mowtiie.faithfully.helper;

import android.content.Context;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

/**
 * Gallery photos in the Cloudflare R2 bucket the website uses, through {@link WorkerClient}.
 * Blocking calls: run them off the main thread. ADMIN only.
 */
public final class PhotoStorageHelper {

    public static final String PHOTO_BASE_URL = "https://pub-c1d51023022a499387032279c20ac678.r2.dev";

    private static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new Random();

    public static class Uploaded {
        public final String imageUrl, thumbnailUrl, storagePath, thumbnailPath;

        Uploaded(String storagePath, String thumbnailPath) {
            this.storagePath = storagePath;
            this.thumbnailPath = thumbnailPath;
            this.imageUrl = PHOTO_BASE_URL + "/" + storagePath;
            this.thumbnailUrl = PHOTO_BASE_URL + "/" + thumbnailPath;
        }
    }

    private PhotoStorageHelper() {}

    public static boolean isHosted(String url) {
        return url != null && url.startsWith(PHOTO_BASE_URL + "/");
    }

    public static String keyFromUrl(String url) {
        return url.substring(PHOTO_BASE_URL.length() + 1);
    }

    /** Unique R2 keys behind a photo's URLs; legacy Firebase URLs are ignored. */
    public static Set<String> hostedKeys(String imageUrl, String thumbnailUrl) {
        Set<String> keys = new LinkedHashSet<>();
        if (isHosted(imageUrl))     keys.add(keyFromUrl(imageUrl));
        if (isHosted(thumbnailUrl)) keys.add(keyFromUrl(thumbnailUrl));
        return keys;
    }

    public static Uploaded upload(Context ctx, byte[] full, byte[] thumb) throws IOException {
        requireAdmin(ctx);

        String name = System.currentTimeMillis() + "_" + randomSuffix() + ".jpg";
        String fullKey  = "gallery/" + name;
        String thumbKey = "gallery/thumbs/" + name;

        call("PUT", fullKey, full);
        try {
            call("PUT", thumbKey, thumb != null ? thumb : full);
        } catch (IOException e) {
            try {
                call("DELETE", fullKey, null);
            } catch (IOException ignored) {
            }
            throw e;
        }
        return new Uploaded(fullKey, thumbKey);
    }

    public static void deleteKeys(Context ctx, Collection<String> keys) throws IOException {
        requireAdmin(ctx);
        for (String key : keys) call("DELETE", key, null);
    }

    private static void call(String method, String key, byte[] body) throws IOException {
        WorkerClient.call(method, key, body, "image/jpeg");
    }

    private static void requireAdmin(Context ctx) {
        if (AuthHelper.currentRole(ctx) != AuthHelper.Role.ADMIN) {
            throw new SecurityException("Only the admin can change photos");
        }
    }

    private static String randomSuffix() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(NAME_CHARS.charAt(RANDOM.nextInt(NAME_CHARS.length())));
        return sb.toString();
    }
}
