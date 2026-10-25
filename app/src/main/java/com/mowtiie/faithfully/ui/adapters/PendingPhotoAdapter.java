package com.mowtiie.faithfully.ui.adapters;

import android.content.Context;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.mowtiie.faithfully.R;

import java.util.List;

/** The photos chosen in AddPhotoActivity: a thumbnail, a caption and an upload status for each. */
public class PendingPhotoAdapter extends RecyclerView.Adapter<PendingPhotoAdapter.PhotoViewHolder> {

    public enum State { PENDING, WORKING, DONE, ERROR }

    public static class Item {
        public final Uri uri;
        public String caption = "";
        public State state = State.PENDING;
        public String message = "";

        public Item(Uri uri) {
            this.uri = uri;
        }
    }

    public interface OnRemoveListener {
        void onRemove(Item item);
    }

    private final Context context;
    private final List<Item> items;
    private final OnRemoveListener listener;
    private boolean busy;

    public PendingPhotoAdapter(Context context, List<Item> items, OnRemoveListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    /** While uploading, captions and removal are locked. */
    public void setBusy(boolean busy) {
        this.busy = busy;
        notifyDataSetChanged();
    }

    public void refresh(Item item) {
        int index = items.indexOf(item);
        if (index >= 0) notifyItemChanged(index);
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Item item = items.get(position);

        holder.bound = null;
        holder.etCaption.setText(item.caption);
        holder.bound = item;

        Glide.with(context).load(item.uri).centerCrop().into(holder.thumb);

        holder.etCaption.setEnabled(!busy && item.state != State.DONE);
        holder.btnRemove.setEnabled(!busy);
        holder.btnRemove.setAlpha(busy ? 0.38f : 1f);
        holder.btnRemove.setOnClickListener(v -> listener.onRemove(item));

        int colorAttr = item.state == State.ERROR ? androidx.appcompat.R.attr.colorError
                : com.google.android.material.R.attr.colorOnSurfaceVariant;
        holder.tvStatus.setTextColor(MaterialColors.getColor(holder.tvStatus, colorAttr));
        switch (item.state) {
            case WORKING:
                holder.tvStatus.setText(item.message);
                break;
            case DONE:
                holder.tvStatus.setText(R.string.photos_item_done);
                break;
            case ERROR:
                holder.tvStatus.setText(item.message);
                break;
            default:
                holder.tvStatus.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextInputEditText etCaption;
        final MaterialTextView tvStatus;
        final ImageButton btnRemove;
        @Nullable Item bound;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumb     = itemView.findViewById(R.id.iv_thumb);
            etCaption = itemView.findViewById(R.id.et_caption);
            tvStatus  = itemView.findViewById(R.id.tv_status);
            btnRemove = itemView.findViewById(R.id.btn_remove);

            etCaption.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (bound != null) bound.caption = s.toString();
                }
            });
        }
    }
}
