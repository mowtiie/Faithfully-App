package com.mowtiie.faithfully.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.helper.AuthHelper;

public class NoticeAdapter extends RecyclerView.Adapter<NoticeAdapter.NoticeViewHolder> {

    private final String message;

    public NoticeAdapter(String message) {
        this.message = message;
    }

    /** Banner for READER and GUEST; ADMIN never sees one. */
    public static NoticeAdapter forRole(Context ctx, AuthHelper.Role role) {
        return new NoticeAdapter(ctx.getString(
                role == AuthHelper.Role.GUEST ? R.string.notice_guest : R.string.notice_reader));
    }

    @NonNull
    @Override
    public NoticeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notice, parent, false);
        return new NoticeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoticeViewHolder holder, int position) {
        holder.tvNotice.setText(message);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    static class NoticeViewHolder extends RecyclerView.ViewHolder {
        final TextView tvNotice;

        NoticeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNotice = itemView.findViewById(R.id.tv_notice);
        }
    }
}
