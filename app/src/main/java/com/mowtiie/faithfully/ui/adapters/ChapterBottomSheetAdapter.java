package com.mowtiie.faithfully.ui.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Chapter;

import org.jspecify.annotations.NonNull;

import java.util.List;

public class ChapterBottomSheetAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_CHAPTER = 0;
    private static final int TYPE_REMOVE = 1;

    private final Context context;
    private final List<Chapter> chapters;
    private final OnChapterSelectedListener listener;

    public interface OnChapterSelectedListener {
        void onChapterSelected(Chapter chapter);
        void onRemoveFromChapterSelected();
    }

    public ChapterBottomSheetAdapter(Context context, List<Chapter> chapters, OnChapterSelectedListener listener) {
        this.context = context;
        this.chapters = chapters;
        this.listener = listener;
    }

    @Override
    public RecyclerView.@NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bottom_sheet_chapter, parent, false);
        return new ChapterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.@NonNull ViewHolder holder, int position) {
        ChapterViewHolder vh = (ChapterViewHolder) holder;

        if (getItemViewType(position) == TYPE_CHAPTER) {
            Chapter chapter = chapters.get(position);
            vh.tvTitle.setText(chapter.getTitle());

            vh.ivIcon.setImageResource(R.drawable.ic_folder);
            int primaryColor = context.getResources().getColor(R.color.md_theme_primary, context.getTheme());
            vh.ivIcon.setImageTintList(ColorStateList.valueOf(primaryColor));

            vh.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onChapterSelected(chapter);
            });
        } else {
            vh.tvTitle.setText("Remove from chapter");
            int errorColor = context.getResources().getColor(android.R.color.holo_red_dark, context.getTheme());
            vh.tvTitle.setTextColor(errorColor);

            vh.ivIcon.setImageResource(R.drawable.ic_folder_off);
            vh.ivIcon.setImageTintList(ColorStateList.valueOf(errorColor));

            vh.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onRemoveFromChapterSelected();
            });
        }
    }

    @Override
    public int getItemCount() {
        return chapters.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return (position == chapters.size()) ? TYPE_REMOVE : TYPE_CHAPTER;
    }

    static class ChapterViewHolder extends RecyclerView.ViewHolder {
        MaterialTextView tvTitle;
        ImageView ivIcon;

        public ChapterViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            ivIcon = itemView.findViewById(R.id.iv_icon);
        }
    }
}