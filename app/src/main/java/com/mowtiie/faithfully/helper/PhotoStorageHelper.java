package com.mowtiie.faithfully.helper;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

/**
 * Talks to the Cloudflare R2 upload Worker the website uses.
 * Blocking calls: run them off the main thread. ADMIN only.
 * Authenticates with the Firebase ID token; no R2 secrets live in the app.
 */
public final class PhotoStorageHelper {

    public static final String UPLOAD_WORKER_URL = "https://faithfully-upload.mowtiie-dev.workers.dev";
    public static final String PHOTO_BASE_URL    = "https://pub-c1d51023022a499387032279c20ac678.r2.dev";

    private static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new Random();
    private static final int TIMEOUT_MS = 30_000;

    public static class StorageException extends IOException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

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

        call(ctx, "PUT", fullKey, full);
        try {
            call(ctx, "PUT", thumbKey, thumb != null ? thumb : full);
        } catch (IOException e) {
            try {
                call(ctx, "DELETE", fullKey, null);
            } catch (IOException ignored) {
            }
            throw e;
        }
        return new Uploaded(fullKey, thumbKey);
    }

    public static void deleteKeys(Context ctx, Collection<String> keys) throws IOException {
        requireAdmin(ctx);
        for (String key : keys) call(ctx, "DELETE", key, null);
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

    private static String token(boolean forceRefresh) throws IOException {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) throw new StorageException("You are signed out. Sign in and try again.", null);
        try {
            return Tasks.await(user.getIdToken(forceRefresh)).getToken();
        } catch (Exception e) {
            throw new StorageException("Could not get your sign-in token. Try again.", e);
        }
    }

    private static void call(Context ctx, String method, String key, byte[] body) throws IOException {
        int status = send(method, key, body, token(false));
        if (status == 401) status = send(method, key, body, token(true));
        if (status == 401) throw new StorageException(describe(401, ""), null);
    }

    /** Returns the HTTP status (2xx or 401); throws StorageException for anything else. */
    private static int send(String method, String key, byte[] body, String token) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(UPLOAD_WORKER_URL + "/" + key).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Authorization", "Bearer " + token);

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "image/jpeg");
                conn.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body);
                }
            }

            int status = conn.getResponseCode();
            if (status == 401) return 401;
            if (status < 200 || status >= 300) {
                throw new StorageException(describe(status, readErrorCode(conn)), null);
            }
            return status;
        } catch (StorageException e) {
            throw e;
        } catch (IOException e) {
            throw new StorageException("Could not reach the upload service. Check your connection and try again.", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readErrorCode(HttpURLConnection conn) {
        try (InputStream in = conn.getErrorStream()) {
            if (in == null) return "";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new JSONObject(out.toString(StandardCharsets.UTF_8.name())).optString("error", "");
        } catch (Exception e) {
            return "";
        }
    }

    private static String describe(int status, String code) {
        if (status == 401 || status == 403) return "The upload service refused your sign-in. Sign out, sign back in, and try again.";
        if (status == 413) return "That image is too large.";
        if (status == 415) return "That image format is not supported.";
        return "The upload service returned an error" + (code.isEmpty() ? "" : " (" + code + ")") + ". Try again.";
    }
}
