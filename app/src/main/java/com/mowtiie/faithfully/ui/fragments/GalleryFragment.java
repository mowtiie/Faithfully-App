package com.mowtiie.faithfully.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Photo;
import com.mowtiie.faithfully.databinding.FragmentGalleryBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.DocFields;
import com.mowtiie.faithfully.helper.GallerySession;
import com.mowtiie.faithfully.helper.MockDataLoader;
import com.mowtiie.faithfully.helper.PhotoStorageHelper;
import com.mowtiie.faithfully.ui.activities.AddPhotoActivity;
import com.mowtiie.faithfully.ui.activities.PhotoViewerActivity;
import com.mowtiie.faithfully.ui.adapters.NoticeAdapter;
import com.mowtiie.faithfully.ui.adapters.PhotoAdapter;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GalleryFragment extends Fragment implements PhotoAdapter.OnPhotoActionListener {

    private FragmentGalleryBinding binding;
    private ListenerRegistration registration;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private PhotoAdapter adapter;
    private List<Photo> photoList;
    private ItemTouchHelper itemTouchHelper;
    private boolean isDragging = false;
    private boolean isAdmin;
    private AuthHelper.Role role;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentGalleryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context ctx = requireContext();
        role = AuthHelper.currentRole(ctx);
        isAdmin = role == AuthHelper.Role.ADMIN;
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        if (!isAdmin) {
            binding.fabAdd.hide();
        } else {
            binding.fabAdd.setOnClickListener(v -> startActivity(new Intent(ctx, AddPhotoActivity.class)));
        }

        photoList = new ArrayList<>();
        adapter = new PhotoAdapter(ctx, photoList, this, isAdmin);

        GridLayoutManager glm = new GridLayoutManager(ctx, 2);
        binding.recyclerView.setLayoutManager(glm);

        if (isAdmin) {
            binding.recyclerView.setAdapter(adapter);
            setupDragAndDrop();
        } else {
            // The banner is the first item and spans both grid columns.
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return position == 0 ? 2 : 1;
                }
            });
            binding.recyclerView.setAdapter(new ConcatAdapter(NoticeAdapter.forRole(ctx, role), adapter));
        }

        if (role == AuthHelper.Role.GUEST) {
            showMock();
            return;
        }

        loadPhotos();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        binding = null;
    }

    // Guests see the sample photos and never touch Firestore.
    private void showMock() {
        photoList.clear();
        photoList.addAll(MockDataLoader.getGallery(requireContext()));
        adapter.notifyDataSetChanged();
        binding.tvEmpty.setVisibility(photoList.isEmpty() ? View.VISIBLE : View.GONE);
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

        registration = db.collection("gallery")
                .orderBy("order", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);

                    if (error != null) {
                        Toast.makeText(requireContext(), "Error: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (isDragging) return;

                    photoList.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            photoList.add(new Photo(
                                    doc.getId(),
                                    DocFields.string(doc, "imageUrl"),
                                    DocFields.string(doc, "thumbnailUrl"),
                                    DocFields.string(doc, "caption"),
                                    DocFields.number(doc, "order", 0),
                                    DocFields.timestamp(doc, "uploadedAt"),
                                    DocFields.string(doc, "storagePath"),
                                    DocFields.string(doc, "thumbnailPath")
                            ));
                        }
                    }

                    adapter.notifyDataSetChanged();
                    binding.tvEmpty.setVisibility(photoList.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    @Override
    public void onTap(Photo photo) {
        GallerySession.set(photoList);
        Intent intent = new Intent(requireContext(), PhotoViewerActivity.class);
        intent.putExtra(PhotoViewerActivity.EXTRA_INDEX, Math.max(0, photoList.indexOf(photo)));
        intent.putExtra("image_url", photo.getImageUrl());
        intent.putExtra("caption",   photo.getCaption());
        startActivity(intent);
    }

    @Override
    public void onEditCaption(Photo photo) {
        if (!isAdmin) {
            return;
        }

        View editCaptionView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_caption, null);
        TextInputEditText inputCaption = editCaptionView.findViewById(R.id.input_caption);
        String currentCaption = photo.getCaption() != null ? photo.getCaption() : "";
        inputCaption.setText(currentCaption);
        inputCaption.setSelection(currentCaption.length());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Caption")
                .setView(editCaptionView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null);

        AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(dialog -> alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newCaption = Objects.requireNonNull(inputCaption.getText()).toString().trim();
            db.collection("gallery").document(photo.getId())
                    .update("caption", newCaption.isEmpty() ? null : newCaption)
                    .addOnSuccessListener(unused -> Toast.makeText(requireContext(), "Caption updated", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            alertDialog.dismiss();
        }));
        alertDialog.show();
    }

    @Override
    public void onDelete(Photo photo) {
        if (!isAdmin) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete photo")
                .setMessage(PhotoStorageHelper.isHosted(photo.getImageUrl())
                        ? "The photo and its thumbnail will be removed for good."
                        : "Delete this photo permanently?")
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

        batch.commit().addOnFailureListener(e -> Toast.makeText(requireContext(), "Failed to save order: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void deletePhoto(Photo photo) {
        if (!isAdmin) {
            return;
        }

        if (PhotoStorageHelper.isHosted(photo.getImageUrl())) {
            deleteHostedPhoto(photo);
        } else {
            deleteLegacyPhoto(photo);
        }
    }

    // R2 files go first so a failure leaves the document in place and the delete can be retried.
    private void deleteHostedPhoto(Photo photo) {
        Context ctx = requireContext();
        android.app.Activity activity = requireActivity();
        new Thread(() -> {
            try {
                PhotoStorageHelper.deleteKeys(ctx,
                        PhotoStorageHelper.hostedKeys(photo.getImageUrl(), photo.getThumbnailUrl()));
            } catch (IOException e) {
                activity.runOnUiThread(() -> Toast.makeText(ctx, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }

            activity.runOnUiThread(() -> db.collection("gallery").document(photo.getId())
                    .delete()
                    .addOnSuccessListener(unused -> Toast.makeText(ctx, "Photo deleted", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(ctx, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()));
        }).start();
    }

    private void deleteLegacyPhoto(Photo photo) {
        db.collection("gallery").document(photo.getId())
                .delete()
                .addOnSuccessListener(unused -> {
                    if (photo.getStoragePath() != null) {
                        storage.getReference(photo.getStoragePath()).delete();
                    }
                    if (photo.getThumbnailPath() != null) {
                        storage.getReference(photo.getThumbnailPath()).delete();
                    }
                    Toast.makeText(requireContext(), "Photo deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}