package com.mowtiie.faithfully.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Card;
import com.mowtiie.faithfully.data.Chapter;
import com.mowtiie.faithfully.helper.ChapterDbHelper;

import java.util.Collections;
import java.util.List;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {

    public interface OnCardActionListener {
        void onEdit(Card card);
        void onDelete(Card card);
        void onChangeChapter(Card card);
        void onReordered(List<Card> cards);
    }

    private final List<Card> cards;
    private final OnCardActionListener listener;
    private final ChapterDbHelper dbHelper;
    private final boolean isAdmin;

    public CardAdapter(Context context, List<Card> cards, OnCardActionListener listener, boolean isAdmin) {
        this.cards = cards;
        this.listener = listener;
        this.dbHelper = ChapterDbHelper.getInstance(context);
        this.isAdmin = isAdmin;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        Card card = cards.get(position);

        holder.tvTitle.setText(card.getTitle());
        holder.tvDate.setText(card.getDateLabel());
        holder.tvMessage.setText(card.getMessage());

        if (card.getChapterId() != null && !card.getChapterId().isEmpty()) {
            Chapter chapter = dbHelper.getById(card.getChapterId());
            if (chapter != null) {
                holder.chipChapter.setText(chapter.getTitle());
                holder.chipChapter.setVisibility(View.VISIBLE);
            } else {
                holder.chipChapter.setVisibility(View.GONE);
            }
        } else {
            holder.chipChapter.setText("Unassigned");
            holder.chipChapter.setVisibility(View.VISIBLE);
        }

        holder.tvMessage.setVisibility(View.GONE);
        holder.layoutActions.setVisibility(View.GONE);

        holder.cardView.setOnClickListener(v -> {
            boolean expanded = holder.tvMessage.getVisibility() == View.VISIBLE;
            holder.tvMessage.setVisibility(expanded ? View.GONE : View.VISIBLE);

            if (isAdmin) {
                holder.layoutActions.setVisibility(expanded ? View.GONE : View.VISIBLE);
            } else {
                holder.layoutActions.setVisibility(View.GONE);
            }
        });

        if (isAdmin) {
            holder.btnChangeChapter.setOnClickListener(v -> listener.onChangeChapter(card));
            holder.btnEdit.setOnClickListener(v   -> listener.onEdit(card));
            holder.btnDelete.setOnClickListener(v -> listener.onDelete(card));
        }
    }

    public void moveItem(int from, int to) {
        Collections.swap(cards, from, to);
        notifyItemMoved(from, to);
    }

    public void onDropComplete() { listener.onReordered(cards); }

    @Override
    public int getItemCount() { return cards.size(); }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView tvTitle, tvDate, tvMessage;
        Chip chipChapter;
        View layoutActions;
        MaterialButton btnChangeChapter, btnEdit, btnDelete;

        CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvMessage = itemView.findViewById(R.id.tv_message);
            chipChapter = itemView.findViewById(R.id.chip_chapter);
            layoutActions = itemView.findViewById(R.id.layout_actions);
            btnChangeChapter = itemView.findViewById(R.id.btn_change_chapter);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
