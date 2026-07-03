package com.mowtiie.faithfully.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.ActivityBackupBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupActivity extends AppCompatActivity {

    private static final String TAG = "FaithfullyBackup";

    private static final String[] COLLECTIONS_TO_BACKUP  = { "cards", "chapters", "gallery" };
    private static final String[] IMAGE_COLLECTION_NAMES = { "gallery" };

    private static final int    BACKUP_VERSION = 1;
    private static final String DATA_JSON      = "data.json";
    private static final String IMAGES_DIR     = "images/";

    private static final String K_VERSION     = "version";
    private static final String K_EXPORTED_AT = "exportedAt";
    private static final String K_ID          = "_id";
    private static final String K_ZIP_FULL    = "_zipFull";
    private static final String K_ZIP_THUMB   = "_zipThumb";

    private ActivityBackupBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private FirebaseFirestore db;
    private FirebaseStorage   storage;
    private BackupFragment    fragment;

    private final ActivityResultLauncher<Intent> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) doExport(uri);
                }
            });

    private final ActivityResultLauncher<Intent> importLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) confirmAndImport(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityBackupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db      = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        fragment = new BackupFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.backup_container, fragment)
                .commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        binding = null;
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    void launchExportPicker() {
        String suggestedName = "faithfully-backup-" +
                DateFormat.format("yyyyMMdd-HHmmss", new Date()) + ".zip";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
        exportLauncher.launch(intent);
    }

    void launchImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{ "application/zip", "application/json" });
        importLauncher.launch(intent);
    }

    private void doExport(Uri uri) {
        toast("Fetching data...");
        fetchAllCollections(snapshots ->
                executor.execute(() -> writeZip(uri, snapshots)));
    }

    private interface FetchAllCallback {
        void onFetched(Map<String, QuerySnapshot> snapshots);
    }

    private void fetchAllCollections(FetchAllCallback cb) {
        Map<String, QuerySnapshot> collected = new HashMap<>();
        fetchNext(0, collected, cb);
    }

    private void fetchNext(int i, Map<String, QuerySnapshot> collected, FetchAllCallback cb) {
        if (i >= COLLECTIONS_TO_BACKUP.length) { cb.onFetched(collected); return; }
        String name = COLLECTIONS_TO_BACKUP[i];
        db.collection(name).get()
                .addOnSuccessListener(snap -> {
                    collected.put(name, snap);
                    fetchNext(i + 1, collected, cb);
                })
                .addOnFailureListener(e -> onError("Export failed while fetching " + name, e));
    }

    private void writeZip(Uri uri, Map<String, QuerySnapshot> snapshots) {
        try (OutputStream fileOut = getContentResolver().openOutputStream(uri);
             ZipOutputStream zip = new ZipOutputStream(fileOut)) {

            if (fileOut == null) throw new Exception("openOutputStream returned null");

            JSONObject root = new JSONObject();
            root.put(K_VERSION,     BACKUP_VERSION);
            root.put(K_EXPORTED_AT, System.currentTimeMillis());

            List<GalleryExportEntry> imagesToDownload = new ArrayList<>();

            for (String collectionName : COLLECTIONS_TO_BACKUP) {
                QuerySnapshot snap = snapshots.get(collectionName);
                if (snap == null) continue;

                boolean hasImages = arrayContains(IMAGE_COLLECTION_NAMES, collectionName);
                JSONArray arr = new JSONArray();

                for (QueryDocumentSnapshot doc : snap) {
                    JSONObject obj = firestoreDocToJson(doc);
                    obj.put(K_ID, doc.getId());

                    if (hasImages) {
                        String imageUrl    = doc.getString("imageUrl");
                        String thumbUrl    = doc.getString("thumbnailUrl");
                        String thumbPath   = doc.getString("thumbnailPath");
                        if (imageUrl != null) {
                            String zipFull  = doc.getId() + ".jpg";
                            String zipThumb = thumbPath != null ? doc.getId() + "_thumb.jpg" : null;
                            obj.put(K_ZIP_FULL,  zipFull);
                            obj.put(K_ZIP_THUMB, zipThumb != null ? zipThumb : JSONObject.NULL);
                            imagesToDownload.add(new GalleryExportEntry(
                                    imageUrl, thumbUrl, zipFull, zipThumb, zipThumb != null));
                        }
                    }
                    arr.put(obj);
                }
                root.put(collectionName, arr);
            }

            zip.putNextEntry(new ZipEntry(DATA_JSON));
            zip.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            int total = imagesToDownload.size();
            for (int i = 0; i < total; i++) {
                GalleryExportEntry entry = imagesToDownload.get(i);
                final int index = i + 1;
                runOnUiThread(() -> toast("Downloading image " + index + " of " + total + "..."));

                byte[] fullBytes = downloadBytes(entry.fullUrl);
                zip.putNextEntry(new ZipEntry(IMAGES_DIR + entry.zipFullName));
                zip.write(fullBytes);
                zip.closeEntry();

                if (entry.hasThumb && entry.thumbUrl != null) {
                    byte[] thumbBytes = downloadBytes(entry.thumbUrl);
                    zip.putNextEntry(new ZipEntry(IMAGES_DIR + entry.zipThumbName));
                    zip.write(thumbBytes);
                    zip.closeEntry();
                }
            }

            zip.finish();
            runOnUiThread(() -> toast("Backup exported 🌻"));

        } catch (Exception e) {
            runOnUiThread(() -> onError("Export failed", e));
        }
    }

    private JSONObject firestoreDocToJson(QueryDocumentSnapshot doc) throws JSONException {
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, Object> e : doc.getData().entrySet()) {
            obj.put(e.getKey(), valueToJson(e.getValue()));
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private Object valueToJson(Object value) throws JSONException {
        if (value == null) return JSONObject.NULL;
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        if (value instanceof Date)      return ((Date) value).getTime();
        if (value instanceof Map) {
            JSONObject nested = new JSONObject();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                nested.put(e.getKey(), valueToJson(e.getValue()));
            }
            return nested;
        }
        if (value instanceof List) {
            JSONArray nested = new JSONArray();
            for (Object item : (List<Object>) value) nested.put(valueToJson(item));
            return nested;
        }
        return value;
    }

    private byte[] downloadBytes(String urlStr) throws Exception {
        try (InputStream in = new URL(urlStr).openStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static class GalleryExportEntry {
        final String fullUrl, thumbUrl, zipFullName, zipThumbName;
        final boolean hasThumb;
        GalleryExportEntry(String fullUrl, String thumbUrl,
                           String zipFullName, String zipThumbName, boolean hasThumb) {
            this.fullUrl      = fullUrl;
            this.thumbUrl     = thumbUrl;
            this.zipFullName  = zipFullName;
            this.zipThumbName = zipThumbName;
            this.hasThumb     = hasThumb;
        }
    }

    private void confirmAndImport(Uri uri) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore backup?")
                .setMessage("This will DELETE all current data, then restore from the file. This cannot be undone.\n\nContinue?")
                .setPositiveButton("Restore", (d, w) -> doImport(uri))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doImport(Uri uri) {
        toast("Reading backup file...");
        executor.execute(() -> {
            try {
                Map<String, byte[]> zipImages = new HashMap<>();
                String dataJson = null;

                try (InputStream in = getContentResolver().openInputStream(uri);
                     ZipInputStream zip = new ZipInputStream(in)) {
                    if (in == null) throw new Exception("Could not open file");

                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = zip.read(buf)) > 0) out.write(buf, 0, n);
                        byte[] bytes = out.toByteArray();

                        if (DATA_JSON.equals(entry.getName())) {
                            dataJson = new String(bytes, StandardCharsets.UTF_8);
                        } else if (entry.getName().startsWith(IMAGES_DIR)) {
                            zipImages.put(entry.getName().substring(IMAGES_DIR.length()), bytes);
                        }
                        zip.closeEntry();
                    }
                }

                if (dataJson == null) {
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        if (in == null) throw new Exception("Could not re-open file");
                        StringBuilder sb = new StringBuilder();
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(in, StandardCharsets.UTF_8));
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        dataJson = sb.toString();
                    }
                }

                JSONObject root = new JSONObject(dataJson);
                int version = root.optInt(K_VERSION, 1);
                if (version > BACKUP_VERSION) {
                    throw new Exception("Backup is from a newer app version");
                }

                Map<String, List<ParsedDoc>> parsed = new HashMap<>();
                for (String name : COLLECTIONS_TO_BACKUP) {
                    JSONArray arr = root.optJSONArray(name);
                    if (arr != null) parsed.put(name, parseCollection(arr, zipImages,
                            arrayContains(IMAGE_COLLECTION_NAMES, name)));
                }

                runOnUiThread(() -> replaceAllAndWrite(parsed));

            } catch (Exception e) {
                runOnUiThread(() -> onError("Import failed", e));
            }
        });
    }

    private void replaceAllAndWrite(Map<String, List<ParsedDoc>> parsed) {
        toast("Deleting current data...");
        fetchAllCollections(snapshots ->
                deleteAllFirestoreAndStorage(snapshots, () -> writeCollections(parsed, 0)));
    }

    private void deleteAllFirestoreAndStorage(Map<String, QuerySnapshot> snapshots, Runnable onDone) {
        for (String name : IMAGE_COLLECTION_NAMES) {
            QuerySnapshot snap = snapshots.get(name);
            if (snap == null) continue;
            for (QueryDocumentSnapshot doc : snap) {
                String sp = doc.getString("storagePath");
                String tp = doc.getString("thumbnailPath");
                if (sp != null) storage.getReference(sp).delete()
                        .addOnFailureListener(e -> Log.w(TAG, "Could not delete " + sp, e));
                if (tp != null) storage.getReference(tp).delete()
                        .addOnFailureListener(e -> Log.w(TAG, "Could not delete " + tp, e));
            }
        }

        List<QueryDocumentSnapshot> toDelete = new ArrayList<>();
        for (QuerySnapshot snap : snapshots.values()) {
            for (QueryDocumentSnapshot d : snap) toDelete.add(d);
        }
        if (toDelete.isEmpty()) { onDone.run(); return; }

        commitDeleteChunk(toDelete, 0, onDone);
    }

    private void commitDeleteChunk(List<QueryDocumentSnapshot> toDelete,
                                   int startIndex, Runnable onDone) {
        int end = Math.min(startIndex + 500, toDelete.size());
        WriteBatch batch = db.batch();
        for (int i = startIndex; i < end; i++) batch.delete(toDelete.get(i).getReference());
        batch.commit()
                .addOnSuccessListener(unused -> {
                    if (end >= toDelete.size()) onDone.run();
                    else                         commitDeleteChunk(toDelete, end, onDone);
                })
                .addOnFailureListener(e -> onError("Delete failed", e));
    }

    private void writeCollections(Map<String, List<ParsedDoc>> parsed, int i) {
        if (i >= COLLECTIONS_TO_BACKUP.length) {
            toast("Restore complete 🌻");
            return;
        }
        String name = COLLECTIONS_TO_BACKUP[i];
        List<ParsedDoc> docs = parsed.get(name);
        if (docs == null || docs.isEmpty()) { writeCollections(parsed, i + 1); return; }

        toast("Writing " + name + " (" + docs.size() + ")...");

        if (arrayContains(IMAGE_COLLECTION_NAMES, name)) {
            uploadImagesThenWrite(name, docs, 0, () -> writeCollections(parsed, i + 1));
        } else {
            writeInBatches(name, docs, 0, () -> writeCollections(parsed, i + 1));
        }
    }

    private void writeInBatches(String collection, List<ParsedDoc> docs,
                                int startIndex, Runnable onDone) {
        int end = Math.min(startIndex + 500, docs.size());
        WriteBatch batch = db.batch();
        for (int i = startIndex; i < end; i++) {
            ParsedDoc pd = docs.get(i);
            batch.set(db.collection(collection).document(pd.id), pd.data);
        }
        batch.commit()
                .addOnSuccessListener(unused -> {
                    if (end >= docs.size()) onDone.run();
                    else                     writeInBatches(collection, docs, end, onDone);
                })
                .addOnFailureListener(e -> onError("Write failed", e));
    }

    private void uploadImagesThenWrite(String collection, List<ParsedDoc> docs,
                                       int i, Runnable onDone) {
        if (i >= docs.size()) { onDone.run(); return; }
        ParsedDoc pd = docs.get(i);

        toast("Uploading image " + (i + 1) + " of " + docs.size() + "...");

        if (pd.fullBytes == null) {
            db.collection(collection).document(pd.id).set(pd.data)
                    .addOnSuccessListener(unused -> uploadImagesThenWrite(collection, docs, i + 1, onDone))
                    .addOnFailureListener(e -> onError("Write failed", e));
            return;
        }

        long ts = System.currentTimeMillis();
        String fullPath  = collection + "/" + ts + "_" + i + ".jpg";
        String thumbPath = pd.thumbBytes != null ? collection + "/" + ts + "_" + i + "_thumb.jpg" : null;

        StorageReference fullRef = storage.getReference(fullPath);
        fullRef.putBytes(pd.fullBytes)
                .addOnSuccessListener(t ->
                        fullRef.getDownloadUrl().addOnSuccessListener(fullUrl -> {
                            pd.data.put("imageUrl",    fullUrl.toString());
                            pd.data.put("storagePath", fullPath);

                            if (thumbPath != null) {
                                StorageReference thumbRef = storage.getReference(thumbPath);
                                thumbRef.putBytes(pd.thumbBytes)
                                        .addOnSuccessListener(t2 ->
                                                thumbRef.getDownloadUrl().addOnSuccessListener(thumbUrl -> {
                                                    pd.data.put("thumbnailUrl",  thumbUrl.toString());
                                                    pd.data.put("thumbnailPath", thumbPath);
                                                    db.collection(collection).document(pd.id).set(pd.data)
                                                            .addOnSuccessListener(u -> uploadImagesThenWrite(collection, docs, i + 1, onDone))
                                                            .addOnFailureListener(e -> onError("Write failed", e));
                                                }))
                                        .addOnFailureListener(e -> onError("Thumb upload failed", e));
                            } else {
                                db.collection(collection).document(pd.id).set(pd.data)
                                        .addOnSuccessListener(u -> uploadImagesThenWrite(collection, docs, i + 1, onDone))
                                        .addOnFailureListener(e -> onError("Write failed", e));
                            }
                        }))
                .addOnFailureListener(e -> onError("Image upload failed", e));
    }

    private List<ParsedDoc> parseCollection(JSONArray arr, Map<String, byte[]> zipImages,
                                            boolean hasImages) throws JSONException {
        List<ParsedDoc> result = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String id = obj.optString(K_ID, null);
            if (id == null) continue;

            byte[] fullBytes  = null;
            byte[] thumbBytes = null;
            if (hasImages) {
                String zipFull = obj.optString(K_ZIP_FULL, null);
                if (zipFull != null) fullBytes = zipImages.get(zipFull);
                if (!obj.isNull(K_ZIP_THUMB)) {
                    String zipThumb = obj.getString(K_ZIP_THUMB);
                    thumbBytes = zipImages.get(zipThumb);
                }
            }

            Map<String, Object> data = jsonObjectToMap(obj);
            data.remove(K_ID);
            data.remove(K_ZIP_FULL);
            data.remove(K_ZIP_THUMB);
            if (hasImages && fullBytes != null) {
                data.remove("imageUrl");
                data.remove("thumbnailUrl");
                data.remove("storagePath");
                data.remove("thumbnailPath");
            }

            result.add(new ParsedDoc(id, data, fullBytes, thumbBytes));
        }
        return result;
    }

    private Map<String, Object> jsonObjectToMap(JSONObject obj) throws JSONException {
        Map<String, Object> map = new HashMap<>();
        java.util.Iterator<String> keys = obj.keys();
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

    private static class ParsedDoc {
        final String id;
        final Map<String, Object> data;
        final byte[] fullBytes, thumbBytes;
        ParsedDoc(String id, Map<String, Object> data, byte[] fullBytes, byte[] thumbBytes) {
            this.id = id; this.data = data; this.fullBytes = fullBytes; this.thumbBytes = thumbBytes;
        }
    }

    void confirmClearAll() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear all data?")
                .setMessage("This will PERMANENTLY DELETE everything, including any image files in Storage. This cannot be undone.\n\nMake sure you've exported a backup first.")
                .setPositiveButton("Continue", (d, w) -> confirmClearAllStep2())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearAllStep2() {
        final EditText input = new EditText(this);
        input.setHint("Type DELETE to confirm");
        new MaterialAlertDialogBuilder(this)
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
        fetchAllCollections(snapshots -> {
            int total = 0;
            for (QuerySnapshot s : snapshots.values()) total += s.size();
            if (total == 0) { toast("Nothing to delete — already empty 🌻"); return; }

            toast("Deleting " + total + " items...");
            deleteAllFirestoreAndStorage(snapshots, () -> toast("All data cleared 🌻"));
        });
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private void onError(String context, Exception e) {
        Log.e(TAG, context, e);
        Toast.makeText(this, context + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private static boolean arrayContains(String[] arr, String value) {
        for (String s : arr) if (s.equals(value)) return true;
        return false;
    }

    public static class BackupFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            setPreferencesFromResource(R.xml.preferences_backup, rootKey);
        }

        @Override
        public boolean onPreferenceTreeClick(@NonNull Preference preference) {
            if (!(getActivity() instanceof BackupActivity)) return super.onPreferenceTreeClick(preference);
            BackupActivity act = (BackupActivity) getActivity();

            String key = preference.getKey();
            if (key == null) return super.onPreferenceTreeClick(preference);

            switch (key) {
                case "export":    act.launchExportPicker(); return true;
                case "restore":   act.launchImportPicker(); return true;
                case "clear_all": act.confirmClearAll();    return true;
                default: return super.onPreferenceTreeClick(preference);
            }
        }
    }
}