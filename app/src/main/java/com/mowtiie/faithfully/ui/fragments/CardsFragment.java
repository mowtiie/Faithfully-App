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
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Card;
import com.mowtiie.faithfully.data.Chapter;
import com.mowtiie.faithfully.databinding.FragmentCardsBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.ChapterDbHelper;
import com.mowtiie.faithfully.helper.DocFields;
import com.mowtiie.faithfully.helper.MockDataLoader;
import com.mowtiie.faithfully.helper.ReaderSession;
import com.mowtiie.faithfully.ui.activities.AddCardActivity;
import com.mowtiie.faithfully.ui.activities.ReaderActivity;
import com.mowtiie.faithfully.ui.adapters.CardAdapter;
import com.mowtiie.faithfully.ui.adapters.ChapterBottomSheetAdapter;
import com.mowtiie.faithfully.ui.adapters.NoticeAdapter;

import java.util.ArrayList;
import java.util.List;

/** The letters in one chapter. Reached from ChaptersFragment with chapter_id and chapter_title arguments. */
public class CardsFragment extends Fragment implements CardAdapter.OnCardActionListener {

    private FragmentCardsBinding binding;
    private FirebaseFirestore db;
    private ChapterDbHelper dbHelper;
    private ListenerRegistration registration;

    private CardAdapter adapter;
    private List<Card> cardList;
    private boolean isDragging = false;
    private boolean isAdmin;
    private AuthHelper.Role role;

    private String chapterId;
    private String chapterTitle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCardsBinding.inflate(inflater, container, false);
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

        Bundle args = requireArguments();
        chapterId = args.getString("chapter_id");
        chapterTitle = args.getString("chapter_title");

        if (!isAdmin) {
            binding.fabAdd.hide();
        } else {
            binding.fabAdd.setOnClickListener(v -> {
                Intent intent = new Intent(ctx, AddCardActivity.class);
                intent.putExtra("chapter_id", chapterId);
                intent.putExtra("chapter_title", chapterTitle);
                startActivity(intent);
            });
        }

        cardList = new ArrayList<>();
        adapter = new CardAdapter(ctx, cardList, this, isAdmin);
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

        loadCards();
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

    // Guests see the sample letters for this chapter and never touch Firestore.
    private void showMock() {
        cardList.clear();
        cardList.addAll(MockDataLoader.getCards(requireContext(), chapterId));
        adapter.notifyDataSetChanged();
        binding.tvEmpty.setVisibility(cardList.isEmpty() ? View.VISIBLE : View.GONE);
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
                    int color = requireContext().getResources().getColor(R.color.md_theme_primaryContainer, requireContext().getTheme());
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

    private void loadCards() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);

        registration = db.collection("cards")
                .whereEqualTo("chapterId", chapterId)
                .orderBy("order", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    binding.recyclerView.setVisibility(View.VISIBLE);

                    if (error != null) {
                        Toast.makeText(requireContext(), "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (isDragging) return;

                    cardList.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            cardList.add(new Card(
                                    doc.getId(),
                                    DocFields.string(doc, "title"),
                                    DocFields.string(doc, "message"),
                                    DocFields.string(doc, "dateLabel"),
                                    DocFields.timestamp(doc, "date"),
                                    DocFields.number(doc, "order", 0),
                                    DocFields.string(doc, "chapterId")
                            ));
                        }
                    }

                    adapter.notifyDataSetChanged();
                    binding.tvEmpty.setVisibility(cardList.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    @Override
    public void onRead(Card card) {
        ReaderSession.set(chapterTitle, cardList);
        Intent intent = new Intent(requireContext(), ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_INDEX, Math.max(0, cardList.indexOf(card)));
        startActivity(intent);
    }

    @Override
    public void onEdit(Card card) {
        if (!isAdmin) {
            return;
        }

        Intent intent = new Intent(requireContext(), AddCardActivity.class);
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
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete card")
                .setMessage("Delete \"" + card.getTitle() + "\"?")
                .setPositiveButton("Delete", (d, w) -> deleteCard(card))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onChangeChapter(Card card) {
        if (!isAdmin) return;
        Context ctx = requireContext();
        List<Chapter> chapters = dbHelper.getAll();

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(ctx);
        View sheetView = LayoutInflater.from(ctx).inflate(R.layout.bottom_sheet_chapter, null);

        RecyclerView rvChapters = sheetView.findViewById(R.id.rv_chapters);
        rvChapters.setLayoutManager(new LinearLayoutManager(ctx));

        ChapterBottomSheetAdapter sheetAdapter = new ChapterBottomSheetAdapter(ctx, chapters, new ChapterBottomSheetAdapter.OnChapterSelectedListener() {
            @Override
            public void onChapterSelected(Chapter chapter) {
                updateCardChapter(card, chapter.getId());
                bottomSheetDialog.dismiss();
            }

            @Override
            public void onRemoveFromChapterSelected() {
                updateCardChapter(card, null);
                bottomSheetDialog.dismiss();
            }
        });

        rvChapters.setAdapter(sheetAdapter);
        bottomSheetDialog.setContentView(sheetView);

        BottomSheetBehavior<?> behavior = bottomSheetDialog.getBehavior();
        behavior.setFitToContents(true);

        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // No action needed during sliding transitions
            }
        });

        bottomSheetDialog.show();
    }

    private void updateCardChapter(Card card, String newChapterId) {
        Context ctx = requireContext();
        db.collection("cards")
                .document(card.getId())
                .update("chapterId", newChapterId)
                .addOnSuccessListener(unused -> {
                    String msg = newChapterId == null
                            ? "Removed from chapter"
                            : "Moved to chapter";
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(ctx, "Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    private void deleteCard(Card card) {
        Context ctx = requireContext();
        db.collection("cards")
                .document(card.getId())
                .delete()
                .addOnSuccessListener(unused ->
                        Toast.makeText(ctx, "Card deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(ctx, "Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    @Override
    public void onReordered(List<Card> reordered) {
        if (!isAdmin) return;
        Context ctx = requireContext();
        WriteBatch batch = db.batch();
        for (int i = 0; i < reordered.size(); i++) {
            batch.update(
                    db.collection("cards").document(reordered.get(i).getId()),
                    "order", (long) i
            );
        }
        batch.commit().addOnFailureListener(e ->
                Toast.makeText(ctx, "Failed to save order: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
    }
}
