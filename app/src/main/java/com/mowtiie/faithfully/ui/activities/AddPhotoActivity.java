package com.mowtiie.faithfully.ui.activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.ActivityAddPhotoBinding;
import com.mowtiie.faithfully.util.ImageUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AddPhotoActivity extends AppCompatActivity {

    private ActivityAddPhotoBinding binding;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

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
        EdgeToEdge.enable(this);
        binding = ActivityAddPhotoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

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

                runOnUiThread(() -> uploadBytes(fullBytes, thumbBytes));
            } catch (IOException e) {
                runOnUiThread(() -> {
                    setLoading(false, null);
                    Toast.makeText(this, "Failed to read image: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void uploadBytes(byte[] fullBytes, byte[] thumbBytes) {
        long now = System.currentTimeMillis();
        String fullPath  = "gallery/" + now + ".jpg";
        String thumbPath = "gallery/" + now + "_thumb.jpg";

        StorageReference fullRef  = storage.getReference(fullPath);
        StorageReference thumbRef = storage.getReference(thumbPath);

        setLoading(true, "Uploading full image...");

        fullRef.putBytes(fullBytes)
                .addOnSuccessListener(taskSnapshot -> {
                    fullRef.getDownloadUrl().addOnSuccessListener(fullUrl -> {

                        setLoading(true, "Uploading thumbnail...");

                        thumbRef.putBytes(thumbBytes)
                                .addOnSuccessListener(thumbSnapshot -> {
                                    thumbRef.getDownloadUrl().addOnSuccessListener(thumbUrl -> {
                                        saveFirestoreDoc(
                                                fullUrl.toString(), thumbUrl.toString(),
                                                fullPath, thumbPath);
                                    });
                                })
                                .addOnFailureListener(this::onUploadError);
                    });
                })
                .addOnFailureListener(this::onUploadError);
    }

    private void saveFirestoreDoc(String imageUrl, String thumbnailUrl, String storagePath, String thumbnailPath) {
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
                    data.put("imageUrl", imageUrl);
                    data.put("thumbnailUrl", thumbnailUrl);
                    data.put("storagePath", storagePath);
                    data.put("thumbnailPath", thumbnailPath);
                    data.put("caption", caption.isEmpty() ? null : caption);
                    data.put("order", nextOrder);
                    data.put("uploadedAt", Timestamp.now());

                    db.collection("gallery").add(data)
                            .addOnSuccessListener(ref -> {
                                setLoading(false, null);
                                Toast.makeText(this, "Photo uploaded! 🐱", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(this::onUploadError);
                })
                .addOnFailureListener(this::onUploadError);
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