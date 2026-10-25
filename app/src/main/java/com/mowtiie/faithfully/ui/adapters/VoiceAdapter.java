package com.mowtiie.faithfully.ui.adapters;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.VoiceClip;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.VoiceFormat;

import java.util.List;
import java.util.Map;

public class VoiceAdapter extends RecyclerView.Adapter<VoiceAdapter.VoiceViewHolder> {

    public enum State { IDLE, LOADING, PLAYING, PAUSED, ERROR }

    public interface OnVoiceActionListener {
        void onPlayPause(VoiceClip clip);
        void onEditCaption(VoiceClip clip);
        void onDelete(VoiceClip clip);
    }

    private static final float DISABLED_ALPHA = 0.38f;

    private final Context context;
    private final List<VoiceClip> clips;
    private final Map<String, String> players;
    private final OnVoiceActionListener listener;
    private final boolean isAdmin;
    private final boolean isGuest;

    private String activeId;
    private State state = State.IDLE;
    private int positionMs;
    private int durationMs;
    private String errorMessage;

    public VoiceAdapter(Context context, List<VoiceClip> clips, Map<String, String> players,
                        OnVoiceActionListener listener, AuthHelper.Role role) {
        this.context = context;
        this.clips = clips;
        this.players = players;
        this.listener = listener;
        this.isAdmin = role == AuthHelper.Role.ADMIN;
        this.isGuest = role == AuthHelper.Role.GUEST;
    }

    /** Which clip is playing and how far along. Only the rows that change are rebound. */
    public void setPlayback(@Nullable String id, State newState, int positionMs, int durationMs, @Nullable String error) {
        String previous = activeId;
        this.activeId = newState == State.IDLE ? null : id;
        this.state = newState;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
        this.errorMessage = error;

        notifyClip(previous);
        if (activeId != null && !activeId.equals(previous)) notifyClip(activeId);
    }

    private void notifyClip(@Nullable String id) {
        if (id == null) return;
        for (int i = 0; i < clips.size(); i++) {
            if (id.equals(clips.get(i).getId())) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public VoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_voice, parent, false);
        return new VoiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VoiceViewHolder holder, int position) {
        VoiceClip clip = clips.get(position);
        boolean active = clip.getId().equals(activeId);
        State rowState = active ? state : State.IDLE;
        boolean playable = clip.getAudioPath() != null;

        if (clip.getCaption().isEmpty()) {
            holder.tvCaption.setText(R.string.voice_untitled);
            holder.tvCaption.setAlpha(0.7f);
        } else {
            holder.tvCaption.setText(clip.getCaption());
            holder.tvCaption.setAlpha(1f);
        }

        String sender = players.get(clip.getUid());
        holder.tvSender.setText(sender != null ? sender : "🎙️");
        holder.tvSender.setContentDescription(context.getString(R.string.voice_sent_by, sender != null ? sender : ""));
        holder.tvDate.setText(dateLabel(clip));

        double total = active && durationMs > 0 ? durationMs / 1000.0 : clip.getDuration();
        double played = active && (rowState == State.PLAYING || rowState == State.PAUSED) ? positionMs / 1000.0 : 0;
        holder.tvTime.setText(VoiceFormat.formatClock(played) + " / " + VoiceFormat.formatClock(total));
        holder.progress.setProgress(total > 0 ? (int) Math.min(1000, played / total * 1000) : 0);

        boolean loading = rowState == State.LOADING;
        holder.loading.setVisibility(loading ? View.VISIBLE : View.GONE);
        holder.btnPlay.setIconResource(rowState == State.PLAYING ? R.drawable.ic_pause : R.drawable.ic_play);
        holder.btnPlay.setContentDescription(context.getString(
                loading ? R.string.voice_loading : rowState == State.PLAYING ? R.string.voice_pause : R.string.voice_play));
        holder.btnPlay.setAlpha(playable ? 1f : DISABLED_ALPHA);
        holder.btnPlay.setEnabled(playable);
        holder.btnPlay.setOnClickListener(v -> listener.onPlayPause(clip));

        if (rowState == State.ERROR && errorMessage != null) {
            holder.tvError.setText(errorMessage);
            holder.tvError.setVisibility(View.VISIBLE);
        } else {
            holder.tvError.setVisibility(View.GONE);
        }

        if (isAdmin) {
            holder.cardView.setOnLongClickListener(v -> {
                showActionMenu(clip);
                return true;
            });
        } else {
            holder.cardView.setOnLongClickListener(null);
            holder.cardView.setLongClickable(false);
        }
    }

    private String dateLabel(VoiceClip clip) {
        if (isGuest) return context.getString(R.string.voice_sample);
        if (clip.getCreatedAt() == null) return context.getString(R.string.voice_just_now);
        return DateFormat.getMediumDateFormat(context).format(clip.getCreatedAt().toDate());
    }

    private void showActionMenu(VoiceClip clip) {
        BottomSheetDialog sheet = new BottomSheetDialog(context);
        View sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_voice, null);

        sheetView.findViewById(R.id.voice_action_edit_caption).setOnClickListener(v -> {
            listener.onEditCaption(clip);
            sheet.dismiss();
        });
        sheetView.findViewById(R.id.voice_action_delete).setOnClickListener(v -> {
            listener.onDelete(clip);
            sheet.dismiss();
        });

        sheet.setContentView(sheetView);

        BottomSheetBehavior<?> behavior = sheet.getBehavior();
        behavior.setFitToContents(true);
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View view, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }

            @Override
            public void onSlide(@NonNull View view, float v) {
            }
        });

        sheet.show();
    }

    @Override
    public int getItemCount() {
        return clips.size();
    }

    static class VoiceViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardView;
        final MaterialButton btnPlay;
        final CircularProgressIndicator loading;
        final MaterialTextView tvCaption, tvSender, tvTime, tvDate, tvError;
        final LinearProgressIndicator progress;

        VoiceViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView  = (MaterialCardView) itemView;
            btnPlay   = itemView.findViewById(R.id.btn_play);
            loading   = itemView.findViewById(R.id.loading);
            tvCaption = itemView.findViewById(R.id.tv_caption);
            tvSender  = itemView.findViewById(R.id.tv_sender);
            tvTime    = itemView.findViewById(R.id.tv_time);
            tvDate    = itemView.findViewById(R.id.tv_date);
            tvError   = itemView.findViewById(R.id.tv_error);
            progress  = itemView.findViewById(R.id.progress);
        }
    }
}
