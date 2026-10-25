package com.mowtiie.faithfully.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Card;

import java.util.List;

/** One full-screen letter per page. */
public class ReaderPageAdapter extends RecyclerView.Adapter<ReaderPageAdapter.PageViewHolder> {

    private final List<Card> cards;

    public ReaderPageAdapter(List<Card> cards) {
        this.cards = cards;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reader_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        Card card = cards.get(position);

        String dateLabel = card.getDateLabel();
        holder.tvDate.setText(dateLabel);
        holder.tvDate.setVisibility(dateLabel == null || dateLabel.isEmpty() ? View.GONE : View.VISIBLE);

        holder.tvTitle.setText(card.getTitle());
        holder.tvMessage.setText(card.getMessage());
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate, tvTitle, tvMessage;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate    = itemView.findViewById(R.id.tv_date);
            tvTitle   = itemView.findViewById(R.id.tv_title);
            tvMessage = itemView.findViewById(R.id.tv_message);
        }
    }
}
