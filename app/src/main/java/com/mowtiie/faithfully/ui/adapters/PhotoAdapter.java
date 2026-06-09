package com.mowtiie.faithfully.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Photo;

import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

    public interface OnPhotoActionListener {
        void onTap(Photo photo);
        void onEditCaption(Photo photo);
        void onDelete(Photo photo);
        void onReordered(List<Photo> photos);
    }

    private final List<Photo> photos;
    private final OnPhotoActionListener listener;
    private final boolean isAdmin;
    private final Context context;

    public PhotoAdapter(Context context, List<Photo> photos, OnPhotoActionListener listener, boolean isAdmin) {
        this.context = context;
        this.photos = photos;
        this.listener = listener;
        this.isAdmin = isAdmin;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Photo photo = photos.get(position);

        String urlToLoad = photo.getThumbnailUrl() != null && !photo.getThumbnailUrl().isEmpty()
                ? photo.getThumbnailUrl()
                : photo.getImageUrl();

        Glide.with(context)
                .load(urlToLoad)
                .placeholder(com.google.android.material.R.color.design_default_color_surface)
                .centerCrop()
                .into(holder.imageView);

        if (photo.getCaption() != null && !photo.getCaption().isEmpty()) {
            holder.tvCaption.setText(photo.getCaption());
            holder.tvCaption.setVisibility(View.VISIBLE);
        } else {
            holder.tvCaption.setVisibility(View.GONE);
        }

        holder.cardView.setOnClickListener(v -> listener.onTap(photo));

        if (isAdmin) {
            holder.cardView.setOnLongClickListener(v -> {
                showActionMenu(photo);
                return true;
            });
        } else {
            holder.cardView.setOnLongClickListener(null);
        }
    }

    private void showActionMenu(Photo photo) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
        View sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_photo, null);

        LinearLayout actionEditCaption = sheetView.findViewById(R.id.photo_action_edit_caption);
        LinearLayout actionDeletePhoto = sheetView.findViewById(R.id.photo_action_delete);

        actionEditCaption.setOnClickListener(v -> {
            listener.onEditCaption(photo);
            bottomSheetDialog.dismiss();
        });

        actionDeletePhoto.setOnClickListener(v -> {
            listener.onDelete(photo);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.setContentView(sheetView);

        BottomSheetBehavior<?> bottomSheetBehavior = bottomSheetDialog.getBehavior();
        bottomSheetBehavior.setFitToContents(true);
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@androidx.annotation.NonNull View view, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }

            @Override
            public void onSlide(@androidx.annotation.NonNull View view, float v) {
                // No action needed during sliding transitions
            }
        });

        bottomSheetDialog.show();
    }

    public void moveItem(int from, int to) {
        Collections.swap(photos, from, to);
        notifyItemMoved(from, to);
    }

    public void onDropComplete() { listener.onReordered(photos); }

    @Override
    public int getItemCount() { return photos.size(); }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        ImageView imageView;
        MaterialTextView tvCaption;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView  = (MaterialCardView) itemView;
            imageView = itemView.findViewById(R.id.iv_photo);
            tvCaption = itemView.findViewById(R.id.tv_caption);
        }
    }
}
