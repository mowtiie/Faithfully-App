package com.mowtiie.faithfully.helper;

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

/**
 * Authenticated calls to the Cloudflare R2 Worker the website uses (PUT / GET / DELETE on a key).
 * Blocking: run off the main thread. Authenticates with the Firebase ID token; no R2 secrets live
 * in the app. Mirrors the website's js/worker-client.js.
 */
public final class WorkerClient {

    public static final String UPLOAD_WORKER_URL = "https://faithfully-upload.mowtiie-dev.workers.dev";

    private static final int TIMEOUT_MS = 30_000;

    private WorkerClient() {}

    private static final class Result {
        final int status;
        final byte[] body;

        Result(int status, byte[] body) {
            this.status = status;
            this.body = body;
        }
    }

    /** Returns the response body for GET and null for everything else. */
    public static byte[] call(String method, String key, byte[] body, String contentType) throws IOException {
        Result result = send(method, key, body, contentType, token(false));
        if (result.status == 401) result = send(method, key, body, contentType, token(true));
        if (result.status == 401) throw new StorageException(describe(401, "", key), null);
        return result.body;
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

    /** Returns the HTTP status (2xx or 401); throws StorageException for anything else. */
    private static Result send(String method, String key, byte[] body, String contentType, String token) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(UPLOAD_WORKER_URL + "/" + key).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Authorization", "Bearer " + token);

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", contentType);
                conn.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body);
                }
            }

            int status = conn.getResponseCode();
            if (status == 401) return new Result(401, null);
            if (status < 200 || status >= 300) {
                throw new StorageException(describe(status, readErrorCode(conn), key), null);
            }

            byte[] data = "GET".equals(method) ? readAll(conn.getInputStream()) : null;
            return new Result(status, data);
        } catch (StorageException e) {
            throw e;
        } catch (IOException e) {
            throw new StorageException("Could not reach the upload service. Check your connection and try again.", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
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

    private static String describe(int status, String code, String key) {
        String what = key.startsWith("voice/") ? "recording" : "image";
        if (status == 401 || status == 403) return "The upload service refused your sign-in. Sign out, sign back in, and try again.";
        if (status == 404) return "That recording could not be found.";
        if (status == 413) return "That " + what + " is too large.";
        if (status == 415) return "That " + what + " format is not supported.";
        return "The upload service returned an error" + (code.isEmpty() ? "" : " (" + code + ")") + ". Try again.";
    }
}
