package com.mowtiie.faithfully.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Photo;
import com.mowtiie.faithfully.databinding.ActivityGalleryBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.ui.adapters.PhotoAdapter;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class GalleryActivity extends AppCompatActivity implements PhotoAdapter.OnPhotoActionListener {

    private ActivityGalleryBinding binding;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private PhotoAdapter adapter;
    private List<Photo> photoList;
    private ItemTouchHelper itemTouchHelper;
    private boolean isDragging = false;
    private boolean isAdmin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityGalleryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        isAdmin = AuthHelper.isAdmin();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (!isAdmin) {
            binding.fabAdd.hide();
        } else {
            binding.fabAdd.setOnClickListener(v -> startActivity(new Intent(this, AddPhotoActivity.class)));
        }

        photoList = new ArrayList<>();
        adapter = new PhotoAdapter(this, photoList, this, isAdmin);

        GridLayoutManager glm = new GridLayoutManager(this, 2);
        binding.recyclerView.setLayoutManager(glm);
        binding.recyclerView.setAdapter(adapter);

        if (isAdmin) {
            setupDragAndDrop();
        }

        loadPhotos();
    }

    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {

            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, RecyclerView.ViewHolder vh) {
                int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
                return makeMovementFlags(dragFlags, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, RecyclerView.ViewHolder src, RecyclerView.ViewHolder tgt) {
                adapter.moveItem(src.getAbsoluteAdapterPosition(), tgt.getAbsoluteAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder vh, int dir) {}

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int state) {
                super.onSelectedChanged(vh, state);
                if (state == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    isDragging = true;
                    vh.itemView.setAlpha(0.7f);
                    vh.itemView.setScaleX(1.06f);
                    vh.itemView.setScaleY(1.06f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                vh.itemView.setAlpha(1f);
                vh.itemView.setScaleX(1f);
                vh.itemView.setScaleY(1f);
                isDragging = false;
                adapter.onDropComplete();
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(binding.recyclerView);
    }

    private void loadPhotos() {
        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("gallery")
                .orderBy("order", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    binding.progressBar.setVisibility(View.GONE);

                    if (error != null) {
                        Toast.makeText(this, "Error: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (isDragging) return;

                    photoList.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Long order = doc.getLong("order");
                            photoList.add(new Photo(
                                    doc.getId(),
                                    doc.getString("imageUrl"),
                                    doc.getString("thumbnailUrl"),
                                    doc.getString("caption"),
                                    order != null ? order : 0,
                                    doc.getTimestamp("uploadedAt"),
                                    doc.getString("storagePath"),
                                    doc.getString("thumbnailPath")
                            ));
                        }
                    }

                    adapter.notifyDataSetChanged();
                    binding.tvEmpty.setVisibility(photoList.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    @Override
    public void onTap(Photo photo) {
        Intent intent = new Intent(this, PhotoViewerActivity.class);
        intent.putExtra("image_url", photo.getImageUrl());
        intent.putExtra("caption",   photo.getCaption());
        startActivity(intent);
    }

    @Override
    public void onEditCaption(Photo photo) {
        if (!isAdmin) {
            return;
        }

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(photo.getCaption());
        input.setSelection(input.getText().length());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit caption")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newCaption = input.getText().toString().trim();
                    db.collection("gallery").document(photo.getId())
                            .update("caption", newCaption.isEmpty() ? null : newCaption)
                            .addOnSuccessListener(unused -> Toast.makeText(this, "Caption updated", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDelete(Photo photo) {
        if (!isAdmin) {
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete photo")
                .setMessage("Delete this photo permanently?")
                .setPositiveButton("Delete", (d, w) -> deletePhoto(photo))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onReordered(List<Photo> reordered) {
        if (!isAdmin) {
            return;
        }

        WriteBatch batch = db.batch();
        for (int i = 0; i < reordered.size(); i++) {
            batch.update(db.collection("gallery").document(reordered.get(i).getId()), "order", (long) i);
        }

        batch.commit().addOnFailureListener(e -> Toast.makeText(this, "Failed to save order: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void deletePhoto(Photo photo) {
        db.collection("gallery").document(photo.getId())
                .delete()
                .addOnSuccessListener(unused -> {
                    if (photo.getStoragePath() != null) {
                        storage.getReference(photo.getStoragePath()).delete();
                    }
                    if (photo.getThumbnailPath() != null) {
                        storage.getReference(photo.getThumbnailPath()).delete();
                    }
                    Toast.makeText(this, "Photo deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}