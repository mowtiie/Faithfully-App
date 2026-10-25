package com.mowtiie.faithfully.ui.activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.ActivityAddPhotoBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.PhotoStorageHelper;
import com.mowtiie.faithfully.helper.StorageException;
import com.mowtiie.faithfully.util.ImageUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AddPhotoActivity extends FaithfullyActivity {

    private ActivityAddPhotoBinding binding;
    private FirebaseFirestore db;

    private Uri selectedImageUri;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    showPreview(uri);
                    binding.btnUpload.setEnabled(true);
                }
            });

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

        binding.btnPickImage.setOnClickListener(v ->
                photoPicker.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        binding.btnUpload.setEnabled(false);
        binding.btnUpload.setOnClickListener(v -> uploadPhoto());
    }

    private void showPreview(Uri uri) {
        binding.previewPlaceholder.setVisibility(View.GONE);
        binding.ivPreview.setVisibility(View.VISIBLE);
        Glide.with(this).load(uri).centerCrop().into(binding.ivPreview);
    }

    private void uploadPhoto() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "Pick an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true, "Compressing...");

        new Thread(() -> {
            try {
                byte[] fullBytes  = ImageUtil.processImage(this, selectedImageUri,
                        ImageUtil.FULL_RES_WIDTH, ImageUtil.FULL_RES_QUALITY);
                byte[] thumbBytes = ImageUtil.processImage(this, selectedImageUri,
                        ImageUtil.THUMB_WIDTH, ImageUtil.THUMB_QUALITY);

                runOnUiThread(() -> setLoading(true, "Uploading..."));
                PhotoStorageHelper.Uploaded uploaded =
                        PhotoStorageHelper.upload(this, fullBytes, thumbBytes);

                runOnUiThread(() -> saveFirestoreDoc(uploaded));
            } catch (StorageException e) {
                runOnUiThread(() -> onUploadError(e));
            } catch (IOException e) {
                runOnUiThread(() -> {
                    setLoading(false, null);
                    Toast.makeText(this, "Failed to read image: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void saveFirestoreDoc(PhotoStorageHelper.Uploaded uploaded) {
        setLoading(true, "Saving...");

        db.collection("gallery")
                .orderBy("order", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    long nextOrder = 0;
                    if (!snap.isEmpty()) {
                        Long last = snap.getDocuments().get(0).getLong("order");
                        if (last != null) nextOrder = last + 1;
                    }

                    String caption = binding.etCaption.getText() != null ? binding.etCaption.getText().toString().trim() : "";

                    Map<String, Object> data = new HashMap<>();
                    data.put("imageUrl", uploaded.imageUrl);
                    data.put("thumbnailUrl", uploaded.thumbnailUrl);
                    data.put("storagePath", uploaded.storagePath);
                    data.put("thumbnailPath", uploaded.thumbnailPath);
                    data.put("caption", caption.isEmpty() ? null : caption);
                    data.put("order", nextOrder);
                    data.put("uploadedAt", Timestamp.now());

                    db.collection("gallery").add(data)
                            .addOnSuccessListener(ref -> {
                                setLoading(false, null);
                                Toast.makeText(this, "Photo uploaded! 🐱", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> saveFailed(uploaded, e));
                })
                .addOnFailureListener(e -> saveFailed(uploaded, e));
    }

    private void saveFailed(PhotoStorageHelper.Uploaded uploaded, Exception e) {
        onUploadError(e);
        new Thread(() -> {
            try {
                PhotoStorageHelper.deleteKeys(this,
                        PhotoStorageHelper.hostedKeys(uploaded.imageUrl, uploaded.thumbnailUrl));
            } catch (IOException ignored) {
            }
        }).start();
    }

    private void onUploadError(Exception e) {
        setLoading(false, null);
        Toast.makeText(this, "Upload failed: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading, String message) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (message != null) {
            binding.tvProgress.setText(message);
            binding.tvProgress.setVisibility(View.VISIBLE);
        } else {
            binding.tvProgress.setVisibility(View.GONE);
        }
        binding.btnPickImage.setEnabled(!loading);
        binding.btnUpload.setEnabled(!loading && selectedImageUri != null);
        binding.etCaption.setEnabled(!loading);
    }
}