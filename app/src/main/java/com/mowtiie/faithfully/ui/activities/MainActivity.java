package com.mowtiie.faithfully.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Card;
import com.mowtiie.faithfully.data.Chapter;
import com.mowtiie.faithfully.databinding.ActivityMainBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.ChapterDbHelper;
import com.mowtiie.faithfully.helper.NetworkHelper;
import com.mowtiie.faithfully.ui.adapters.CardAdapter;
import com.mowtiie.faithfully.ui.adapters.ChapterAdapter;
import com.mowtiie.faithfully.ui.adapters.NoticeAdapter;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends FaithfullyActivity implements ChapterAdapter.OnChapterActionListener {

    private ActivityMainBinding binding;

    private FirebaseFirestore db;
    private ChapterDbHelper dbHelper;

    private ChapterAdapter adapter;
    private List<Chapter> chapterList;
    private ItemTouchHelper itemTouchHelper;

    private boolean isDragging = false;
    private boolean isAdmin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        isAdmin = AuthHelper.isAdmin();
        db = FirebaseFirestore.getInstance();
        dbHelper = ChapterDbHelper.getInstance(this);

        setSupportActionBar(binding.toolbar);

        if (!isAdmin) {
            binding.fabAdd.hide();
        } else {
            binding.fabAdd.setOnClickListener(v -> {
                Intent addChapterIntent = new Intent(MainActivity.this, AddChapterActivity.class);
                startActivity(addChapterIntent);
            });
        }

        chapterList = new ArrayList<>();
        adapter = new ChapterAdapter(chapterList, this, isAdmin);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));

        binding.toolbar.setNavigationOnClickListener(v -> {
            if (isAdmin) {
                showLogoutDialog();
            } else {
                AuthHelper.setGuestMode(this, false);
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }
        });

        if (isAdmin) {
            binding.recyclerView.setAdapter(adapter);
            setupDragAndDrop();
        } else {
            NoticeAdapter noticeAdapter = new NoticeAdapter();
            ConcatAdapter concatAdapter = new ConcatAdapter(noticeAdapter, adapter);
            binding.recyclerView.setAdapter(concatAdapter);
        }

        showCached();
        listenFirestore();
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
        if (NetworkHelper.isOffline(this)) {
            Toast.makeText(this, "You are currently offline.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("chapters")
                .orderBy("order", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    binding.progressBar.setVisibility(View.GONE);

                    if (error != null) {
                        Toast.makeText(this, "Error: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (isDragging) return;

                    chapterList.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Long order = doc.getLong("order");
                            chapterList.add(new Chapter(
                                    doc.getId(),
                                    doc.getString("title"),
                                    doc.getString("description"),
                                    order != null ? order : 0
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
                    int color = getResources().getColor(R.color.md_theme_primaryContainer, getTheme());
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
                int color = getResources().getColor(R.color.md_theme_surfaceContainer, getTheme());
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

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(binding.recyclerView);
    }

    @Override
    public void onEdit(Chapter chapter) {
        if (!isAdmin) {
            return;
        }

        Intent intent = new Intent(this, AddChapterActivity.class);
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

        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete chapter")
                .setMessage("Delete \"" + chapter.getTitle() + "\"? Cards inside will NOT be deleted but will become unassigned.")
                .setPositiveButton("Delete", (d, w) -> deleteChapter(chapter))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onOpen(Chapter chapter) {
        Intent intent = new Intent(this, CardsActivity.class);
        intent.putExtra("chapter_id", chapter.getId());
        intent.putExtra("chapter_title", chapter.getTitle());
        startActivity(intent);
    }

    @Override
    public void onReordered(List<Chapter> reordered) {
        if (!isAdmin) return;
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
                        Toast.makeText(this, "Failed to save order: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    private void deleteChapter(Chapter chapter) {
        db.collection("chapters")
                .document(chapter.getId())
                .delete()
                .addOnSuccessListener(unused -> {
                    dbHelper.delete(chapter.getId());
                    Toast.makeText(this, "Chapter deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        boolean isAdmin = AuthHelper.isAdmin();
        menu.findItem(R.id.menu_backup).setVisible(isAdmin);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int selectedMenuItem = item.getItemId();

        if (selectedMenuItem == R.id.menu_gallery) {
            Intent galleryActivity = new Intent(this, GalleryActivity.class);
            startActivity(galleryActivity);
        }

        if (selectedMenuItem == R.id.menu_about) {
            Intent aboutIntent = new Intent(this, AboutActivity.class);
            startActivity(aboutIntent);
        }

        if (selectedMenuItem == R.id.menu_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
        }

        return true;
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Sign out")
                .setMessage("Sign out and return to the login screen?")
                .setPositiveButton("Sign out", (d, w) -> {
                    AuthHelper.signOutEverything(this);
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}