package com.mowtiie.faithfully.helper;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * Voice clips in the private R2 bucket, through {@link WorkerClient}. Blocking: run off the main thread.
 * ADMIN and READER may upload (into their own folder) and download; only ADMIN may delete.
 * Clips have no public address, so playback downloads the file into the app cache first.
 */
public final class VoiceStorageHelper {

    private static final String CACHE_DIR = "voice";
    private static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new Random();

    private VoiceStorageHelper() {}

    public static String newKey(String uid, String extension) {
        return VoiceFormat.newKey(uid, extension, System.currentTimeMillis(), randomSuffix());
    }

    /** Both accounts upload only into their own folder. */
    public static void upload(Context ctx, String key, byte[] audio, String mimeType) throws IOException {
        requireSignedIn(ctx);
        if (!VoiceFormat.isVoiceKey(key)) throw new IllegalArgumentException("Not a voice key: " + key);
        if (!key.startsWith("voice/" + currentUid() + "/")) {
            throw new SecurityException("Recordings can only go in your own folder");
        }
        if (audio.length > VoiceFormat.MAX_BYTES) {
            throw new StorageException("That recording is too large. Try a shorter one.", null);
        }
        WorkerClient.call("PUT", key, audio, mimeType);
    }

    public static byte[] downloadBytes(Context ctx, String key) throws IOException {
        requireSignedIn(ctx);
        byte[] data = WorkerClient.call("GET", key, null, null);
        if (data == null || data.length == 0) throw new StorageException("That recording is empty.", null);
        return data;
    }

    /** The clip as a file in the app cache, fetched through the Worker the first time it is played. */
    public static File download(Context ctx, String key) throws IOException {
        File dir = new File(ctx.getCacheDir(), CACHE_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create the cache folder");

        File file = new File(dir, key.replace('/', '_'));
        if (file.isFile() && file.length() > 0) return file;

        byte[] data = downloadBytes(ctx, key);
        File partial = new File(dir, file.getName() + ".part");
        try (OutputStream out = new FileOutputStream(partial)) {
            out.write(data);
        }
        if (!partial.renameTo(file)) throw new IOException("Could not save the recording");
        return file;
    }

    public static void delete(Context ctx, String key) throws IOException {
        if (AuthHelper.currentRole(ctx) != AuthHelper.Role.ADMIN) {
            throw new SecurityException("Only the admin can delete recordings");
        }
        WorkerClient.call("DELETE", key, null, null);
        new File(new File(ctx.getCacheDir(), CACHE_DIR), key.replace('/', '_')).delete();
    }

    /** Recordings are private to the signed-in session, so they are dropped on sign-out. */
    public static void clearCache(Context ctx) {
        File[] files = new File(ctx.getCacheDir(), CACHE_DIR).listFiles();
        if (files == null) return;
        for (File file : files) //noinspection ResultOfMethodCallIgnored
            file.delete();
    }

    private static AuthHelper.Role requireSignedIn(Context ctx) {
        AuthHelper.Role role = AuthHelper.currentRole(ctx);
        if (role != AuthHelper.Role.ADMIN && role != AuthHelper.Role.READER) {
            throw new SecurityException("Sign in to use voice recordings");
        }
        return role;
    }

    private static String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : "";
    }

    private static String randomSuffix() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(NAME_CHARS.charAt(RANDOM.nextInt(NAME_CHARS.length())));
        return sb.toString();
    }
}
