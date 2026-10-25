package com.mowtiie.faithfully.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Photo;

import java.util.List;

/** One full-size photo per page in the viewer. */
public class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.PageViewHolder> {

    private final Context context;
    private final List<Photo> photos;

    public PhotoPagerAdapter(Context context, List<Photo> photos) {
        this.context = context;
        this.photos = photos;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        Photo photo = photos.get(position);
        String caption = photo.getCaption();

        holder.image.setContentDescription(caption == null || caption.isEmpty() ? context.getString(R.string.viewer_title) : caption);
        Glide.with(context)
                .load(photo.getImageUrl())
                .placeholder(R.color.md_theme_surfaceContainer)
                .fitCenter()
                .into(holder.image);
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        Glide.with(context).clear(holder.image);
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.iv_page);
        }
    }
}
