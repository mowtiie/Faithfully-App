package com.mowtiie.faithfully.ui.activities;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.ActivityAddPhotoBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.PhotoStorageHelper;
import com.mowtiie.faithfully.helper.StorageException;
import com.mowtiie.faithfully.ui.adapters.PendingPhotoAdapter;
import com.mowtiie.faithfully.ui.adapters.PendingPhotoAdapter.Item;
import com.mowtiie.faithfully.ui.adapters.PendingPhotoAdapter.State;
import com.mowtiie.faithfully.util.ImageUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Add several photos at once, each with its own caption, uploaded one after another with a status
 * per photo. A photo that fails can be retried without redoing the ones that worked. Mirrors the
 * website's photo dialog.
 */
public class AddPhotoActivity extends FaithfullyActivity implements PendingPhotoAdapter.OnRemoveListener {

    private static final String TAG = "AddPhoto";
    private static final int MAX_PHOTOS = 20;
    private static final int PROGRESS_MAX = 1000;

    private ActivityAddPhotoBinding binding;
    private FirebaseFirestore db;

    private final List<Item> items = new ArrayList<>();
    private PendingPhotoAdapter adapter;
    private boolean busy;
    private long nextOrder;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS), this::addPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (AuthHelper.currentRole(this) != AuthHelper.Role.ADMIN) {
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        binding = ActivityAddPhotoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        adapter = new PendingPhotoAdapter(this, items, this);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
        binding.tvHint.setText(getString(R.string.photos_hint, MAX_PHOTOS));

        binding.btnPickImages.setOnClickListener(v ->
                photoPicker.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        binding.btnUpload.setOnClickListener(v -> upload());

        refresh();
    }

    private void addPicked(List<Uri> uris) {
        if (busy || uris == null) return;

        boolean full = false;
        for (Uri uri : uris) {
            if (containsUri(uri)) continue;
            if (items.size() >= MAX_PHOTOS) {
                full = true;
                break;
            }
            items.add(new Item(uri));
        }

        adapter.notifyDataSetChanged();
        if (full) Toast.makeText(this, getString(R.string.photos_limit, MAX_PHOTOS), Toast.LENGTH_SHORT).show();
        refresh();
    }

    private boolean containsUri(Uri uri) {
        for (Item item : items) if (item.uri.equals(uri)) return true;
        return false;
    }

    @Override
    public void onRemove(Item item) {
        if (busy) return;
        items.remove(item);
        adapter.notifyDataSetChanged();
        refresh();
    }

    private int count(State state) {
        int n = 0;
        for (Item item : items) if (item.state == state) n++;
        return n;
    }

    private void refresh() {
        if (binding == null) return;

        int waiting = count(State.PENDING);
        int failed  = count(State.ERROR);
        int done    = count(State.DONE);
        int todo    = waiting + failed;

        binding.btnPickImages.setEnabled(!busy);
        binding.btnUpload.setEnabled(!busy && todo > 0);

        if (busy)                binding.btnUpload.setText(R.string.photos_adding);
        else if (todo == 0)      binding.btnUpload.setText(R.string.photos_add);
        else if (waiting == 0)   binding.btnUpload.setText(failed == 1 ? getString(R.string.photos_retry_one) : getString(R.string.photos_retry_many, failed));
        else                     binding.btnUpload.setText(todo == 1 ? getString(R.string.photos_add_one) : getString(R.string.photos_add_many, todo));

        boolean showProgress = busy || done > 0 || failed > 0;
        binding.progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        binding.progress.setProgress(items.isEmpty() ? 0 : (done + failed) * PROGRESS_MAX / items.size());

        if (busy)                         binding.tvSummary.setText(getString(R.string.photos_summary_busy, Math.min(items.size(), done + failed + 1), items.size()));
        else if (done > 0 && failed > 0)  binding.tvSummary.setText(getString(R.string.photos_summary_mixed, done, failed));
        else if (done > 0 && todo == 0)   binding.tvSummary.setText(R.string.photos_summary_done);
        else                              binding.tvSummary.setText("");
    }

    private void upload() {
        if (AuthHelper.currentRole(this) != AuthHelper.Role.ADMIN || busy) return;

        List<Item> queue = new ArrayList<>();
        for (Item item : items) if (item.state == State.PENDING || item.state == State.ERROR) queue.add(item);
        if (queue.isEmpty()) return;

        busy = true;
        adapter.setBusy(true);
        refresh();

        new Thread(() -> {
            try {
                nextOrder = lastOrder() + 1;
            } catch (Exception e) {
                Log.w(TAG, "Could not read the gallery order", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.photos_failed, Toast.LENGTH_LONG).show();
                    endRun();
                });
                return;
            }

            for (Item item : queue) {
                setState(item, State.WORKING, getString(R.string.photos_step_compressing));
                try {
                    uploadOne(item);
                    setState(item, State.DONE, "");
                } catch (Exception e) {
                    Log.w(TAG, "Photo not added", e);
                    setState(item, State.ERROR, e instanceof StorageException && e.getMessage() != null
                            ? e.getMessage() : getString(R.string.photos_failed));
                }
            }

            runOnUiThread(this::endRun);
        }).start();
    }

    private long lastOrder() throws Exception {
        QuerySnapshot snap = Tasks.await(db.collection("gallery")
                .orderBy("order", Query.Direction.DESCENDING)
                .limit(1)
                .get());
        if (snap.isEmpty()) return -1;
        Long last = snap.getDocuments().get(0).getLong("order");
        return last != null ? last : -1;
    }

    private void uploadOne(Item item) throws Exception {
        byte[] full  = ImageUtil.processImage(this, item.uri, ImageUtil.FULL_RES_WIDTH, ImageUtil.FULL_RES_QUALITY);
        byte[] thumb = ImageUtil.processImage(this, item.uri, ImageUtil.THUMB_WIDTH, ImageUtil.THUMB_QUALITY);

        setState(item, State.WORKING, getString(R.string.photos_step_uploading));
        PhotoStorageHelper.Uploaded uploaded = PhotoStorageHelper.upload(this, full, thumb);

        setState(item, State.WORKING, getString(R.string.photos_step_saving));
        String caption = item.caption.trim();

        Map<String, Object> data = new HashMap<>();
        data.put("imageUrl", uploaded.imageUrl);
        data.put("thumbnailUrl", uploaded.thumbnailUrl);
        data.put("storagePath", uploaded.storagePath);
        data.put("thumbnailPath", uploaded.thumbnailPath);
        data.put("caption", caption.isEmpty() ? null : caption);
        data.put("order", nextOrder);
        data.put("uploadedAt", Timestamp.now());

        try {
            Tasks.await(db.collection("gallery").add(data));
        } catch (Exception e) {
            try {
                PhotoStorageHelper.deleteKeys(this, PhotoStorageHelper.hostedKeys(uploaded.imageUrl, uploaded.thumbnailUrl));
            } catch (IOException ignored) {
            }
            throw e;
        }
        nextOrder++;
    }

    private void setState(Item item, State state, String message) {
        runOnUiThread(() -> {
            if (binding == null) return;
            item.state = state;
            item.message = message;
            adapter.refresh(item);
            refresh();
        });
    }

    private void endRun() {
        busy = false;
        if (binding == null) return;

        adapter.setBusy(false);
        refresh();

        if (!items.isEmpty() && count(State.DONE) == items.size()) {
            Toast.makeText(this, R.string.photos_summary_done, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
