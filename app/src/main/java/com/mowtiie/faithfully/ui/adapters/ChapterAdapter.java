package com.mowtiie.faithfully.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Chapter;

import java.util.Collections;
import java.util.List;

public class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder> {

    public interface OnChapterActionListener {
        void onEdit(Chapter chapter);
        void onDelete(Chapter chapter);
        void onOpen(Chapter chapter);
        void onReordered(List<Chapter> chapters);
    }

    private final List<Chapter> chapters;
    private final OnChapterActionListener listener;
    private final boolean isAdmin;

    public ChapterAdapter(List<Chapter> chapters, OnChapterActionListener listener, boolean isAdmin) {
        this.chapters = chapters;
        this.listener = listener;
        this.isAdmin  = isAdmin;
    }

    @NonNull
    @Override
    public ChapterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chapter, parent, false);
        return new ChapterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChapterViewHolder holder, int position) {
        Chapter chapter = chapters.get(position);

        holder.tvTitle.setText(chapter.getTitle());

        String desc = chapter.getDescription();
        if (desc != null && !desc.isEmpty()) {
            holder.tvDescription.setText(desc);
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        if (isAdmin) {
            holder.btnEdit.setVisibility(View.VISIBLE);
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnEdit.setOnClickListener(v -> listener.onEdit(chapter));
            holder.btnDelete.setOnClickListener(v -> listener.onDelete(chapter));
        } else {
            holder.btnEdit.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.GONE);
        }

        holder.cardView.setOnClickListener(v -> listener.onOpen(chapter));
    }

    public void moveItem(int from, int to) {
        Collections.swap(chapters, from, to);
        notifyItemMoved(from, to);
    }

    public void onDropComplete() { listener.onReordered(chapters); }

    @Override
    public int getItemCount() { return chapters.size(); }

    static class ChapterViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        MaterialTextView tvTitle, tvDescription;
        MaterialButton btnEdit, btnDelete;

        ChapterViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            tvTitle = itemView.findViewById(R.id.tv_chapter_title);
            tvDescription = itemView.findViewById(R.id.tv_chapter_description);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}