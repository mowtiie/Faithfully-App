package com.mowtiie.faithfully.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Card;
import com.mowtiie.faithfully.data.Chapter;
import com.mowtiie.faithfully.databinding.ActivityCardsBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.ChapterDbHelper;
import com.mowtiie.faithfully.ui.adapters.CardAdapter;

import java.util.ArrayList;
import java.util.List;

public class CardsActivity extends AppCompatActivity implements CardAdapter.OnCardActionListener {

    private ActivityCardsBinding binding;
    private FirebaseFirestore db;
    private ChapterDbHelper dbHelper;

    private CardAdapter adapter;
    private List<Card> cardList;
    private ItemTouchHelper itemTouchHelper;
    private boolean isDragging = false;
    private boolean isAdmin;

    private String chapterId;
    private String chapterTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityCardsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        isAdmin = AuthHelper.isAdmin();
        db = FirebaseFirestore.getInstance();
        dbHelper = ChapterDbHelper.getInstance(this);

        chapterId = getIntent().getStringExtra("chapter_id");
        chapterTitle = getIntent().getStringExtra("chapter_title");

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(chapterTitle);
        }

        if (!isAdmin) {
            binding.fabAdd.hide();
        } else {
            binding.fabAdd.setOnClickListener(v -> {
                Intent intent = new Intent(CardsActivity.this, AddCardActivity.class);
                intent.putExtra("chapter_id",    chapterId);
                intent.putExtra("chapter_title", chapterTitle);
                startActivity(intent);
            });
        }

        cardList = new ArrayList<>();
        adapter = new CardAdapter(this, cardList, this, isAdmin);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        if (isAdmin) {
            setupDragAndDrop();
        }

        loadCards();
    }

    private void setupDragAndDrop() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {

            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder src, @NonNull RecyclerView.ViewHolder tgt) {
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
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
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

    private void loadCards() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);

        db.collection("cards")
                .whereEqualTo("chapterId", chapterId)
                .orderBy("order", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.recyclerView.setVisibility(View.VISIBLE);

                    if (error != null) {
                        Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (isDragging) return;

                    cardList.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Long order = doc.getLong("order");
                            cardList.add(new Card(
                                    doc.getId(),
                                    doc.getString("title"),
                                    doc.getString("message"),
                                    doc.getString("dateLabel"),
                                    doc.getTimestamp("date"),
                                    order != null ? order : 0,
                                    doc.getString("chapterId")
                            ));
                        }
                    }

                    adapter.notifyDataSetChanged();
                    binding.tvEmpty.setVisibility(cardList.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    @Override
    public void onEdit(Card card) {
        if (!isAdmin) {
            return;
        }

        Intent intent = new Intent(this, AddCardActivity.class);
        intent.putExtra("card_id",         card.getId());
        intent.putExtra("card_title",      card.getTitle());
        intent.putExtra("card_message",    card.getMessage());
        intent.putExtra("card_date_label", card.getDateLabel());
        intent.putExtra("card_order",      card.getOrder());
        intent.putExtra("chapter_id",      chapterId);
        intent.putExtra("chapter_title",   chapterTitle);
        startActivity(intent);
    }

    @Override
    public void onDelete(Card card) {
        if (!isAdmin) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete card")
                .setMessage("Delete \"" + card.getTitle() + "\"?")
                .setPositiveButton("Delete", (d, w) -> deleteCard(card))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onChangeChapter(Card card) {
        if (!isAdmin) return;
        List<Chapter> chapters = dbHelper.getAll();

        String[] labels = new String[chapters.size() + 1];
        for (int i = 0; i < chapters.size(); i++) {
            labels[i] = chapters.get(i).getTitle();
        }
        labels[chapters.size()] = "❌ Remove from chapter";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Move to chapter")
                .setItems(labels, (dialog, which) -> {
                    if (which == chapters.size()) {
                        updateCardChapter(card, null);
                    } else {
                        updateCardChapter(card, chapters.get(which).getId());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateCardChapter(Card card, String newChapterId) {
        db.collection("cards")
                .document(card.getId())
                .update("chapterId", newChapterId)
                .addOnSuccessListener(unused -> {
                    String msg = newChapterId == null
                            ? "Removed from chapter"
                            : "Moved to chapter";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    private void deleteCard(Card card) {
        db.collection("cards")
                .document(card.getId())
                .delete()
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Card deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    @Override
    public void onReordered(List<Card> reordered) {
        if (!isAdmin) return;
        WriteBatch batch = db.batch();
        for (int i = 0; i < reordered.size(); i++) {
            batch.update(
                    db.collection("cards").document(reordered.get(i).getId()),
                    "order", (long) i
            );
        }
        batch.commit().addOnFailureListener(e ->
                Toast.makeText(this, "Failed to save order: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
    }
}