package com.mowtiie.faithfully.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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
        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setItems(new String[]{ "Edit caption", "Delete" }, (d, which) -> {
                    if (which == 0) listener.onEditCaption(photo);
                    else            listener.onDelete(photo);
                })
                .show();
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
