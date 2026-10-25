package com.mowtiie.faithfully.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Chapter;
import com.mowtiie.faithfully.databinding.FragmentChaptersBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.ChapterDbHelper;
import com.mowtiie.faithfully.helper.DocFields;
import com.mowtiie.faithfully.helper.MockDataLoader;
import com.mowtiie.faithfully.helper.NetworkHelper;
import com.mowtiie.faithfully.ui.activities.AddChapterActivity;
import com.mowtiie.faithfully.ui.adapters.ChapterAdapter;
import com.mowtiie.faithfully.ui.adapters.NoticeAdapter;

import java.util.ArrayList;
import java.util.List;

public class ChaptersFragment extends Fragment implements ChapterAdapter.OnChapterActionListener {

    private FragmentChaptersBinding binding;

    private FirebaseFirestore db;
    private ChapterDbHelper dbHelper;
    private ListenerRegistration registration;

    private ChapterAdapter adapter;
    private List<Chapter> chapterList;

    private boolean isDragging = false;
    private boolean isAdmin;
    private AuthHelper.Role role;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChaptersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context ctx = requireContext();
        role = AuthHelper.currentRole(ctx);
        isAdmin = role == AuthHelper.Role.ADMIN;
        db = FirebaseFirestore.getInstance();
        dbHelper = ChapterDbHelper.getInstance(ctx);

        if (!isAdmin) {
            binding.fabAdd.hide();
        } else {
            binding.fabAdd.setOnClickListener(v -> startActivity(new Intent(ctx, AddChapterActivity.class)));
        }

        chapterList = new ArrayList<>();
        adapter = new ChapterAdapter(chapterList, this, isAdmin);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(ctx));

        if (isAdmin) {
            binding.recyclerView.setAdapter(adapter);
            setupDragAndDrop();
        } else {
            NoticeAdapter noticeAdapter = NoticeAdapter.forRole(ctx, role);
            binding.recyclerView.setAdapter(new ConcatAdapter(noticeAdapter, adapter));
        }

        if (role == AuthHelper.Role.GUEST) {
            showMock();
            return;
        }

        showCached();
        listenFirestore();
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

    // Guests see the sample chapters and never touch Firestore or the chapter cache.
    private void showMock() {
        chapterList.clear();
        chapterList.addAll(MockDataLoader.getChapters(requireContext()));
        adapter.notifyDataSetChanged();
        binding.tvEmpty.setVisibility(chapterList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showCached() {
        List<Chapter> cached = dbHelper.getAll();
        if (!cached.isEmpty()) {
            chapterList.clear();
            chapterList.addAll(cached);
            adapter.notifyDataSetChanged();
            binding.tvEmpty.setVisibility(View.GONE);
        }
    }

    private void listenFirestore() {
        if (NetworkHelper.isOffline(requireContext())) {
            Toast.makeText(requireContext(), "You are currently offline.", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        registration = db.collection("chapters")
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

                    chapterList.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            chapterList.add(new Chapter(
                                    doc.getId(),
                                    DocFields.string(doc, "title"),
                                    DocFields.string(doc, "description"),
                                    DocFields.number(doc, "order", 0)
                            ));
                        }
                        dbHelper.upsertAll(chapterList);
                    }

                    adapter.notifyDataSetChanged();
                    binding.tvEmpty.setVisibility(chapterList.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {

            @Override
            public int getMovementFlags(@NonNull RecyclerView rv,
                                        @NonNull RecyclerView.ViewHolder vh) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder src,
                                  @NonNull RecyclerView.ViewHolder tgt) {
                adapter.moveItem(src.getAbsoluteAdapterPosition(), tgt.getAbsoluteAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int state) {
                super.onSelectedChanged(vh, state);
                if (state == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    isDragging = true;
                    MaterialCardView card = (MaterialCardView) vh.itemView;
                    int color = requireContext().getResources().getColor(R.color.md_theme_primaryContainer, requireContext().getTheme());
                    card.setCardBackgroundColor(color);
                    card.setCardElevation(24f);
                    card.setScaleX(1.03f);
                    card.setScaleY(1.03f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                MaterialCardView card = (MaterialCardView) vh.itemView;
                int color = requireContext().getResources().getColor(R.color.md_theme_surfaceContainer, requireContext().getTheme());
                card.setCardBackgroundColor(color);
                card.setCardElevation(4f);
                card.setScaleX(1f);
                card.setScaleY(1f);
                isDragging = false;
                adapter.onDropComplete();
            }

            @Override
            public boolean isLongPressDragEnabled() { return true; }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerView);
    }

    @Override
    public void onEdit(Chapter chapter) {
        if (!isAdmin) {
            return;
        }

        Intent intent = new Intent(requireContext(), AddChapterActivity.class);
        intent.putExtra("chapter_id", chapter.getId());
        intent.putExtra("chapter_title", chapter.getTitle());
        intent.putExtra("chapter_description", chapter.getDescription());
        intent.putExtra("chapter_order", chapter.getOrder());
        startActivity(intent);
    }

    @Override
    public void onDelete(Chapter chapter) {
        if (!isAdmin) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete chapter")
                .setMessage("Delete \"" + chapter.getTitle() + "\"? Cards inside will NOT be deleted but will become unassigned.")
                .setPositiveButton("Delete", (d, w) -> deleteChapter(chapter))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onOpen(Chapter chapter) {
        Bundle args = new Bundle();
        args.putString("chapter_id", chapter.getId());
        args.putString("chapter_title", chapter.getTitle());
        NavHostFragment.findNavController(this).navigate(R.id.action_chapters_to_cards, args);
    }

    @Override
    public void onReordered(List<Chapter> reordered) {
        if (!isAdmin) return;
        Context ctx = requireContext();
        WriteBatch batch = db.batch();
        for (int i = 0; i < reordered.size(); i++) {
            batch.update(
                    db.collection("chapters").document(reordered.get(i).getId()),
                    "order", (long) i
            );
        }
        batch.commit()
                .addOnSuccessListener(unused -> dbHelper.upsertAll(reordered))
                .addOnFailureListener(e ->
                        Toast.makeText(ctx, "Failed to save order: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    private void deleteChapter(Chapter chapter) {
        Context ctx = requireContext();
        db.collection("chapters")
                .document(chapter.getId())
                .delete()
                .addOnSuccessListener(unused -> {
                    dbHelper.delete(chapter.getId());
                    Toast.makeText(ctx, "Chapter deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(ctx, "Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }
}
