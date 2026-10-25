package com.mowtiie.faithfully.helper;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class BackupManager {

    private static final String TAG = "FaithfullyBackup";

    // Same sections and zip layout as the website's js/backup.js
    private static final String   SETTINGS      = "settings";
    private static final String   SETTINGS_ID   = "home";
    private static final String   GALLERY       = "gallery";
    private static final String   VOICE         = "voice";
    private static final String[] SECTIONS      = { "chapters", "cards", GALLERY, "apps", VOICE, SETTINGS };
    private static final String[] CLEAR_COLLECTIONS = { "cards", "chapters", GALLERY };

    private static final int    BACKUP_VERSION = 1;
    private static final String DATA_JSON      = "data.json";
    private static final String IMAGES_DIR     = "images/";
    private static final String AUDIO_DIR      = "audio/";
    private static final int    BATCH_SIZE     = 400;

    private static final String K_VERSION     = "version";
    private static final String K_EXPORTED_AT = "exportedAt";
    private static final String K_ID          = "_id";
    private static final String K_ZIP_FULL    = "_zipFull";
    private static final String K_ZIP_THUMB   = "_zipThumb";
    private static final String K_ZIP_AUDIO   = "_zipAudio";

    private static final Set<String> TIMESTAMP_FIELDS = new HashSet<>(Arrays.asList(
            "date", "uploadedAt", "createdAt", "countdownTarget", "anniversaryStart"));

    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseStorage storage = FirebaseStorage.getInstance();

    /** Backup, restore and clear-all for the admin. Hosted by an activity that owns the file pickers. */
    public BackupManager(Activity activity) {
        this.activity = activity;
    }

    /** Call from the host activity's onDestroy. */
    public void shutdown() {
        executor.shutdownNow();
    }

    public static String suggestedFileName() {
        return "faithfully-backup-" + DateFormat.format("yyyyMMdd-HHmmss", new Date()) + ".zip";
    }

    private boolean isAdmin() {
        return AuthHelper.currentRole(activity) == AuthHelper.Role.ADMIN;
    }


    // ------------------------------------------------------------------
    //  Reading Firestore
    // ------------------------------------------------------------------

    private static class Doc {
        final String id;
        final Map<String, Object> data;
        Doc(String id, Map<String, Object> data) {
            this.id = id;
            this.data = data != null ? data : new HashMap<>();
        }
    }

    /** Blocking: call from the executor. */
    private Map<String, List<Doc>> fetchAll(String[] sections) throws Exception {
        Map<String, List<Doc>> all = new HashMap<>();
        for (String name : sections) {
            List<Doc> docs = new ArrayList<>();
            if (SETTINGS.equals(name)) {
                DocumentSnapshot d = Tasks.await(db.collection(SETTINGS).document(SETTINGS_ID).get());
                if (d.exists()) docs.add(new Doc(d.getId(), d.getData()));
            } else {
                for (DocumentSnapshot d : Tasks.await(db.collection(name).get()).getDocuments()) {
                    docs.add(new Doc(d.getId(), d.getData()));
                }
            }
            all.put(name, docs);
        }
        return all;
    }

    // ------------------------------------------------------------------
    //  Export
    // ------------------------------------------------------------------

    public void export(Uri uri) {
        toast("Fetching data...");
        executor.execute(() -> {
            if (!isAdmin()) return;
            try {
                int[] skipped = writeZip(uri, fetchAll(SECTIONS));
                toastLong(skipped[0] == 0 && skipped[1] == 0
                        ? "Backup exported 🌻"
                        : "Backup exported, but " + skippedSummary(skipped[0], skipped[1]) + " could not be downloaded");
            } catch (Exception e) {
                activity.runOnUiThread(() -> onError("Export failed", e));
            }
        });
    }

    private static String skippedSummary(int photos, int recordings) {
        List<String> parts = new ArrayList<>();
        if (photos > 0)     parts.add(photos + " photo(s)");
        if (recordings > 0) parts.add(recordings + " recording(s)");
        return TextUtils.join(" and ", parts);
    }

    /** Returns how many photos and how many recordings could not be included. */
    private int[] writeZip(Uri uri, Map<String, List<Doc>> all) throws Exception {
        OutputStream fileOut = activity.getContentResolver().openOutputStream(uri);
        if (fileOut == null) throw new Exception("openOutputStream returned null");

        int skipped = 0;
        int skippedAudio = 0;
        try (ZipOutputStream zip = new ZipOutputStream(fileOut)) {
            JSONObject root = new JSONObject();
            root.put(K_VERSION,     BACKUP_VERSION);
            root.put(K_EXPORTED_AT, System.currentTimeMillis());

            List<Doc> gallery = all.get(GALLERY);
            int photoTotal = 0;
            if (gallery != null) {
                for (Doc d : gallery) if (str(d.data.get("imageUrl")) != null) photoTotal++;
            }
            int photoIndex = 0;
            int audioIndex = 0;

            for (String name : SECTIONS) {
                JSONArray arr = new JSONArray();
                List<Doc> docs = all.get(name);
                if (docs != null) {
                    for (Doc doc : docs) {
                        JSONObject obj = mapToJson(doc.data);
                        obj.put(K_ID, doc.id);

                        String imageUrl = GALLERY.equals(name) ? str(doc.data.get("imageUrl")) : null;
                        if (imageUrl != null) {
                            toast("Downloading image " + (++photoIndex) + " of " + photoTotal + "...");
                            if (!addPhotoToZip(zip, doc, obj, imageUrl)) skipped++;
                        }

                        String audioPath = VOICE.equals(name) ? str(doc.data.get("audioPath")) : null;
                        if (audioPath != null) {
                            toast("Downloading recording " + (++audioIndex) + "...");
                            if (!addAudioToZip(zip, doc, obj, audioPath)) skippedAudio++;
                        }
                        arr.put(obj);
                    }
                }
                root.put(name, arr);
            }

            // data.json goes last: it records which photos actually made it into the zip.
            zip.putNextEntry(new ZipEntry(DATA_JSON));
            zip.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new int[]{ skipped, skippedAudio };
    }

    /** Recordings have no public address, so they come down through the Worker. */
    private boolean addAudioToZip(ZipOutputStream zip, Doc doc, JSONObject obj, String audioPath)
            throws IOException, JSONException {
        byte[] audio;
        try {
            audio = VoiceStorageHelper.downloadBytes(activity, audioPath);
        } catch (Exception e) {
            Log.w(TAG, "Recording not included: " + doc.id, e);
            return false;
        }

        String file = doc.id + "." + VoiceFormat.extensionOfKey(audioPath);
        zip.putNextEntry(new ZipEntry(AUDIO_DIR + file));
        zip.write(audio);
        zip.closeEntry();
        obj.put(K_ZIP_AUDIO, file);
        return true;
    }

    /** Photos come down by URL, so R2 and legacy Firebase photos export the same way. */
    private boolean addPhotoToZip(ZipOutputStream zip, Doc doc, JSONObject obj, String imageUrl)
            throws IOException, JSONException {
        byte[] full, thumb = null;
        try {
            full = downloadBytes(imageUrl);
            String thumbUrl = str(doc.data.get("thumbnailUrl"));
            if (thumbUrl != null) thumb = downloadBytes(thumbUrl);
        } catch (Exception e) {
            Log.w(TAG, "Photo not included: " + doc.id, e);
            return false;
        }

        zip.putNextEntry(new ZipEntry(IMAGES_DIR + doc.id + ".jpg"));
        zip.write(full);
        zip.closeEntry();
        obj.put(K_ZIP_FULL, doc.id + ".jpg");

        if (thumb != null) {
            zip.putNextEntry(new ZipEntry(IMAGES_DIR + doc.id + "_thumb.jpg"));
            zip.write(thumb);
            zip.closeEntry();
            obj.put(K_ZIP_THUMB, doc.id + "_thumb.jpg");
        }
        return true;
    }

    private JSONObject mapToJson(Map<String, Object> data) throws JSONException {
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            obj.put(e.getKey(), valueToJson(e.getValue()));
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private Object valueToJson(Object value) throws JSONException {
        if (value == null) return JSONObject.NULL;
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        if (value instanceof Date)      return ((Date) value).getTime();
        if (value instanceof Map)       return mapToJson((Map<String, Object>) value);
        if (value instanceof List) {
            JSONArray nested = new JSONArray();
            for (Object item : (List<Object>) value) nested.put(valueToJson(item));
            return nested;
        }
        return value;
    }

    private byte[] downloadBytes(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(30_000);
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    // ------------------------------------------------------------------
    //  Restore
    // ------------------------------------------------------------------

    public void confirmAndRestore(Uri uri) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Restore backup?")
                .setMessage("This will REPLACE your current chapters, letters, photos and other data with the backup. "
                        + "Anything that is not in the backup is removed. This cannot be undone.\n\nContinue?")
                .setPositiveButton("Restore", (d, w) -> doImport(uri))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doImport(Uri uri) {
        toast("Reading backup file...");
        executor.execute(() -> {
            if (!isAdmin()) return;
            File temp = null;
            ZipFile zip = null;
            try {
                temp = copyToCache(uri);

                String dataJson;
                try {
                    zip = new ZipFile(temp);
                    ZipEntry entry = zip.getEntry(DATA_JSON);
                    if (entry == null) throw new Exception("That zip has no data.json, so it is not a Faithfully backup.");
                    dataJson = readText(zip.getInputStream(entry));
                } catch (ZipException notAZip) {
                    zip = null;
                    try (InputStream in = new java.io.FileInputStream(temp)) {
                        dataJson = readText(in);
                    }
                }

                Map<String, List<JSONObject>> parsed = parseBackup(new JSONObject(dataJson));

                toast("Reading current data...");
                Map<String, List<Doc>> current = fetchAll(SECTIONS);

                int[] results = restore(parsed, zip, current);
                ChapterDbHelper.getInstance(activity).clearAll();

                StringBuilder msg = new StringBuilder("Restore complete 🌻");
                if (results[0] > 0) msg.append("\n").append(results[0]).append(" photo(s) could not be restored.");
                if (results[1] > 0) msg.append("\n").append(results[1]).append(" old photo file(s) could not be removed.");
                if (results[2] > 0) msg.append("\n").append(results[2]).append(" recording(s) could not be restored.");
                if (results[3] > 0) msg.append("\n").append(results[3]).append(" old recording file(s) could not be removed.");
                toastLong(msg.toString());
            } catch (Exception e) {
                activity.runOnUiThread(() -> onError("Import failed", e));
            } finally {
                try { if (zip != null) zip.close(); } catch (IOException ignored) { }
                if (temp != null) //noinspection ResultOfMethodCallIgnored
                    temp.delete();
            }
        });
    }

    private Map<String, List<JSONObject>> parseBackup(JSONObject root) throws Exception {
        if (root.optInt(K_VERSION, 1) > BACKUP_VERSION) {
            throw new Exception("This backup was made by a newer version and cannot be restored here.");
        }

        Map<String, List<JSONObject>> parsed = new HashMap<>();
        for (String name : SECTIONS) {
            if (!root.has(name) || root.isNull(name)) continue;
            JSONArray arr = root.optJSONArray(name);
            if (arr == null) throw new Exception("The \"" + name + "\" section of the backup is not a list.");

            List<JSONObject> list = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.optString(K_ID, "");
                if (id.isEmpty() || id.contains("/")) {
                    throw new Exception("An entry in \"" + name + "\" has no valid id.");
                }
                list.add(obj);
            }
            parsed.put(name, list);
        }

        if (parsed.isEmpty()) throw new Exception("That backup contains none of the expected data.");
        return parsed;
    }

    /**
     * Uploads and writes first, removes old files last, so a failure part-way never
     * leaves the gallery pointing at deleted photos. Returns
     * {failedPhotos, leftoverPhotoFiles, failedRecordings, leftoverRecordingFiles}.
     */
    private int[] restore(Map<String, List<JSONObject>> parsed, @Nullable ZipFile zip,
                          Map<String, List<Doc>> current) throws Exception {
        int failedPhotos = 0;
        int failedAudio = 0;
        List<String> staleAudio = new ArrayList<>();

        Map<String, Map<String, Object>> finalGallery = new HashMap<>();
        for (Doc d : current.get(GALLERY)) finalGallery.put(d.id, d.data);

        for (String name : SECTIONS) {
            List<JSONObject> entries = parsed.get(name);
            if (entries == null) continue;
            toast("Writing " + name + " (" + entries.size() + ")...");

            List<Doc> batchDocs = new ArrayList<>();
            Set<String> backupIds = new HashSet<>();

            for (int i = 0; i < entries.size(); i++) {
                JSONObject obj = entries.get(i);
                String id = obj.getString(K_ID);
                backupIds.add(id);

                Map<String, Object> fields = toFields(obj);
                String zipFull = obj.optString(K_ZIP_FULL, "");

                String zipAudio = obj.optString(K_ZIP_AUDIO, "");
                String audioPath = str(fields.get("audioPath"));
                if (VOICE.equals(name) && !zipAudio.isEmpty() && audioPath != null && zip != null
                        && zip.getEntry(AUDIO_DIR + zipAudio) != null && VoiceFormat.isVoiceKey(audioPath)) {
                    toast("Uploading recording " + (i + 1) + " of " + entries.size() + "...");
                    try {
                        byte[] audio = readEntry(zip, AUDIO_DIR + zipAudio);
                        VoiceStorageHelper.upload(activity, audioPath, audio, VoiceFormat.mimeForKey(audioPath));
                    } catch (IOException e) {
                        Log.w(TAG, "Recording not restored: " + id, e);
                        failedAudio++;
                        continue;
                    }
                }

                if (GALLERY.equals(name) && !zipFull.isEmpty() && zip != null
                        && zip.getEntry(IMAGES_DIR + zipFull) != null) {
                    toast("Uploading image " + (i + 1) + " of " + entries.size() + "...");
                    try {
                        byte[] full = readEntry(zip, IMAGES_DIR + zipFull);
                        String zipThumb = obj.optString(K_ZIP_THUMB, "");
                        byte[] thumb = !zipThumb.isEmpty() && zip.getEntry(IMAGES_DIR + zipThumb) != null
                                ? readEntry(zip, IMAGES_DIR + zipThumb) : null;

                        PhotoStorageHelper.Uploaded up = PhotoStorageHelper.upload(activity, full, thumb);
                        fields.put("imageUrl",      up.imageUrl);
                        fields.put("thumbnailUrl",  up.thumbnailUrl);
                        fields.put("storagePath",   up.storagePath);
                        fields.put("thumbnailPath", up.thumbnailPath);
                    } catch (IOException e) {
                        Log.w(TAG, "Photo not restored: " + id, e);
                        failedPhotos++;
                        continue;
                    }
                    Tasks.await(db.collection(name).document(id).set(fields));
                    finalGallery.put(id, fields);
                    continue;
                }

                batchDocs.add(new Doc(id, fields));
                if (GALLERY.equals(name)) finalGallery.put(id, fields);
            }

            commitSets(name, batchDocs);

            List<String> stale = new ArrayList<>();
            for (Doc d : current.get(name)) if (!backupIds.contains(d.id)) stale.add(d.id);
            commitDeletes(name, stale);
            if (GALLERY.equals(name)) for (String id : stale) finalGallery.remove(id);
            if (VOICE.equals(name)) {
                for (Doc d : current.get(name)) {
                    String path = str(d.data.get("audioPath"));
                    if (!backupIds.contains(d.id) && path != null) staleAudio.add(path);
                }
            }
        }

        int leftoverAudio = 0;
        if (!staleAudio.isEmpty()) {
            toast("Cleaning up old recordings...");
            for (String path : staleAudio) {
                try {
                    VoiceStorageHelper.delete(activity, path);
                } catch (IOException e) {
                    Log.w(TAG, "Old recording not removed: " + path, e);
                    leftoverAudio++;
                }
            }
        }

        int leftover = 0;
        if (parsed.containsKey(GALLERY)) {
            toast("Cleaning up old photo files...");
            leftover = removeReplacedPhotoFiles(current.get(GALLERY), finalGallery);
        }
        return new int[]{ failedPhotos, leftover, failedAudio, leftoverAudio };
    }

    /** Removes files of photos that were replaced or dropped; returns how many R2 files are left behind. */
    private int removeReplacedPhotoFiles(List<Doc> before, Map<String, Map<String, Object>> after) {
        Set<String> keep = new HashSet<>();
        for (Map<String, Object> d : after.values()) {
            keep.addAll(PhotoStorageHelper.hostedKeys(str(d.get("imageUrl")), str(d.get("thumbnailUrl"))));
        }

        Set<String> doomed = new LinkedHashSet<>();
        for (Doc old : before) {
            Map<String, Object> next = after.get(old.id);
            String oldUrl = str(old.data.get("imageUrl"));
            if (next != null && equalsNullable(oldUrl, str(next.get("imageUrl")))) continue;

            if (PhotoStorageHelper.isHosted(oldUrl)) {
                doomed.addAll(PhotoStorageHelper.hostedKeys(oldUrl, str(old.data.get("thumbnailUrl"))));
            } else {
                deleteLegacyFiles(old.data);
            }
        }
        doomed.removeAll(keep);

        try {
            PhotoStorageHelper.deleteKeys(activity, doomed);
            return 0;
        } catch (IOException e) {
            Log.w(TAG, "Old photo files not removed", e);
            return doomed.size();
        }
    }

    private void deleteLegacyFiles(Map<String, Object> data) {
        for (String field : new String[]{ "storagePath", "thumbnailPath" }) {
            String path = str(data.get(field));
            if (path == null) continue;
            try {
                Tasks.await(storage.getReference(path).delete());
            } catch (Exception e) {
                Log.w(TAG, "Could not delete " + path, e);
            }
        }
    }

    private Map<String, Object> toFields(JSONObject obj) throws JSONException {
        Map<String, Object> fields = jsonObjectToMap(obj);
        fields.remove(K_ID);
        fields.remove(K_ZIP_FULL);
        fields.remove(K_ZIP_THUMB);
        fields.remove(K_ZIP_AUDIO);
        for (String key : TIMESTAMP_FIELDS) {
            Object v = fields.get(key);
            if (v instanceof Number) fields.put(key, new Timestamp(new Date(((Number) v).longValue())));
        }
        return fields;
    }

    private void commitSets(String collection, List<Doc> docs) throws Exception {
        for (int i = 0; i < docs.size(); i += BATCH_SIZE) {
            WriteBatch batch = db.batch();
            for (Doc d : docs.subList(i, Math.min(i + BATCH_SIZE, docs.size()))) {
                batch.set(db.collection(collection).document(d.id), d.data);
            }
            Tasks.await(batch.commit());
        }
    }

    private void commitDeletes(String collection, List<String> ids) throws Exception {
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            WriteBatch batch = db.batch();
            for (String id : ids.subList(i, Math.min(i + BATCH_SIZE, ids.size()))) {
                batch.delete(db.collection(collection).document(id));
            }
            Tasks.await(batch.commit());
        }
    }

    private Map<String, Object> jsonObjectToMap(JSONObject obj) throws JSONException {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            map.put(k, jsonValueToObject(obj.get(k)));
        }
        return map;
    }

    private Object jsonValueToObject(Object val) throws JSONException {
        if (val == JSONObject.NULL) return null;
        if (val instanceof JSONObject) return jsonObjectToMap((JSONObject) val);
        if (val instanceof JSONArray) {
            JSONArray a = (JSONArray) val;
            List<Object> list = new ArrayList<>(a.length());
            for (int i = 0; i < a.length(); i++) list.add(jsonValueToObject(a.get(i)));
            return list;
        }
        return val;
    }

    private File copyToCache(Uri uri) throws IOException {
        File temp = File.createTempFile("restore", ".zip", activity.getCacheDir());
        try (InputStream in = activity.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(temp)) {
            if (in == null) throw new IOException("Could not open file");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return temp;
    }

    private byte[] readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) throw new IOException("Missing " + name);
        try (InputStream in = zip.getInputStream(entry);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private String readText(InputStream in) throws IOException {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = stream.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    // ------------------------------------------------------------------
    //  Clear all
    // ------------------------------------------------------------------

    public void confirmClearAll() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Clear all data?")
                .setMessage("This will PERMANENTLY DELETE everything, including every photo file. This cannot be undone.\n\nMake sure you've exported a backup first.")
                .setPositiveButton("Continue", (d, w) -> confirmClearAllStep2())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearAllStep2() {
        final EditText input = new EditText(activity);
        input.setHint("Type DELETE to confirm");
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Are you absolutely sure?")
                .setMessage("Type DELETE in the field below to confirm.")
                .setView(input)
                .setPositiveButton("Delete everything", (d, w) -> {
                    if ("DELETE".equals(input.getText().toString().trim())) doClearAll();
                    else toast("Confirmation text didn't match — nothing was deleted.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doClearAll() {
        toast("Fetching current data...");
        executor.execute(() -> {
            if (!isAdmin()) return;
            try {
                Map<String, List<Doc>> all = fetchAll(CLEAR_COLLECTIONS);
                int total = 0;
                for (List<Doc> docs : all.values()) total += docs.size();
                if (total == 0) { toast("Nothing to delete — already empty 🌻"); return; }

                toast("Deleting " + total + " items...");

                // Photo files first: if the Worker refuses, the documents stay and it can be retried.
                Set<String> hostedKeys = new LinkedHashSet<>();
                for (Doc d : all.get(GALLERY)) {
                    String imageUrl = str(d.data.get("imageUrl"));
                    if (PhotoStorageHelper.isHosted(imageUrl)) {
                        hostedKeys.addAll(PhotoStorageHelper.hostedKeys(imageUrl, str(d.data.get("thumbnailUrl"))));
                    } else {
                        deleteLegacyFiles(d.data);
                    }
                }
                PhotoStorageHelper.deleteKeys(activity, hostedKeys);

                for (String name : CLEAR_COLLECTIONS) {
                    List<String> ids = new ArrayList<>();
                    for (Doc d : all.get(name)) ids.add(d.id);
                    commitDeletes(name, ids);
                }

                ChapterDbHelper.getInstance(activity).clearAll();
                toast("All data cleared 🌻");
            } catch (Exception e) {
                activity.runOnUiThread(() -> onError("Clear failed", e));
            }
        });
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private void toast(String msg) {
        activity.runOnUiThread(() -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }

    private void toastLong(String msg) {
        activity.runOnUiThread(() -> Toast.makeText(activity, msg, Toast.LENGTH_LONG).show());
    }

    private void onError(String context, Exception e) {
        Log.e(TAG, context, e);
        Toast.makeText(activity, context + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    @Nullable
    private static String str(Object value) {
        return value instanceof String && !((String) value).isEmpty() ? (String) value : null;
    }

    private static boolean equalsNullable(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }
}
