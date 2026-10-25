package com.mowtiie.faithfully.ui.adapters;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textview.MaterialTextView;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.AppEntry;
import com.mowtiie.faithfully.helper.AppLinks;

import java.util.List;

/** The Apps showcase. The admin gets a long-press menu to edit, move and delete once the list is saved. */
public class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.AppViewHolder> {

    public interface OnAppActionListener {
        void onEdit(AppEntry app);
        void onDelete(AppEntry app);
        void onMove(AppEntry app, int delta);
    }

    private static final String FALLBACK_ICON = "📱";

    private final Context context;
    private final List<AppEntry> apps;
    private final OnAppActionListener listener;
    private boolean editable;

    public AppsAdapter(Context context, List<AppEntry> apps, OnAppActionListener listener) {
        this.context = context;
        this.apps = apps;
        this.listener = listener;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppEntry app = apps.get(position);

        holder.tvName.setText(app.getName());
        holder.tvTagline.setText(app.getTagline());
        holder.tvTagline.setVisibility(app.getTagline().isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvDescription.setText(app.getDescription());
        holder.tvDescription.setVisibility(app.getDescription().isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvVersion.setText(app.getVersion());
        holder.tvVersion.setVisibility(app.getVersion().isEmpty() ? View.GONE : View.VISIBLE);

        holder.tvIconEmoji.setText(app.getIcon().isEmpty() ? FALLBACK_ICON : app.getIcon());
        String iconUrl = AppLinks.resolve(app.getIconImage());
        if (iconUrl != null) {
            Glide.with(context).load(iconUrl).into(holder.ivIcon);
        } else {
            Glide.with(context).clear(holder.ivIcon);
            holder.ivIcon.setImageDrawable(null);
        }

        bindLink(holder.btnDownload, AppLinks.resolve(app.getDownloadUrl()));
        bindLink(holder.btnSource, AppLinks.resolve(app.getSourceUrl()));
        holder.layoutLinks.setVisibility(
                holder.btnDownload.getVisibility() == View.GONE && holder.btnSource.getVisibility() == View.GONE
                        ? View.GONE : View.VISIBLE);

        if (editable) {
            holder.cardView.setOnLongClickListener(v -> {
                showActionMenu(app, position);
                return true;
            });
        } else {
            holder.cardView.setOnLongClickListener(null);
            holder.cardView.setLongClickable(false);
        }
    }

    private void bindLink(MaterialButton button, String url) {
        if (url == null) {
            button.setVisibility(View.GONE);
            button.setOnClickListener(null);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> open(url));
    }

    private void open(String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.apps_link_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showActionMenu(AppEntry app, int position) {
        BottomSheetDialog sheet = new BottomSheetDialog(context);
        View sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_app, null);

        View up = sheetView.findViewById(R.id.app_action_up);
        View down = sheetView.findViewById(R.id.app_action_down);
        up.setEnabled(position > 0);
        up.setAlpha(position > 0 ? 1f : 0.38f);
        down.setEnabled(position < apps.size() - 1);
        down.setAlpha(position < apps.size() - 1 ? 1f : 0.38f);

        sheetView.findViewById(R.id.app_action_edit).setOnClickListener(v -> {
            listener.onEdit(app);
            sheet.dismiss();
        });
        up.setOnClickListener(v -> {
            listener.onMove(app, -1);
            sheet.dismiss();
        });
        down.setOnClickListener(v -> {
            listener.onMove(app, 1);
            sheet.dismiss();
        });
        sheetView.findViewById(R.id.app_action_delete).setOnClickListener(v -> {
            listener.onDelete(app);
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
        return apps.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardView;
        final MaterialTextView tvName, tvVersion, tvTagline, tvDescription, tvIconEmoji;
        final ShapeableImageView ivIcon;
        final View layoutLinks;
        final MaterialButton btnDownload, btnSource;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView      = (MaterialCardView) itemView;
            tvName        = itemView.findViewById(R.id.tv_name);
            tvVersion     = itemView.findViewById(R.id.tv_version);
            tvTagline     = itemView.findViewById(R.id.tv_tagline);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvIconEmoji   = itemView.findViewById(R.id.tv_icon_emoji);
            ivIcon        = itemView.findViewById(R.id.iv_icon);
            layoutLinks   = itemView.findViewById(R.id.layout_links);
            btnDownload   = itemView.findViewById(R.id.btn_download);
            btnSource     = itemView.findViewById(R.id.btn_source);
        }
    }
}
