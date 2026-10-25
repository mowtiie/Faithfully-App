package com.mowtiie.faithfully.ui.activities;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.ActivityAddAppBinding;
import com.mowtiie.faithfully.helper.AuthHelper;

import java.util.HashMap;
import java.util.Map;

/** Add or edit an app in the showcase. ADMIN only. Writes the same fields the website's editor does. */
public class AddAppActivity extends FaithfullyActivity {

    public static final String EXTRA_ID           = "app_id";
    public static final String EXTRA_NAME         = "app_name";
    public static final String EXTRA_TAGLINE      = "app_tagline";
    public static final String EXTRA_DESCRIPTION  = "app_description";
    public static final String EXTRA_VERSION      = "app_version";
    public static final String EXTRA_ICON         = "app_icon";
    public static final String EXTRA_ICON_IMAGE   = "app_icon_image";
    public static final String EXTRA_DOWNLOAD_URL = "app_download_url";
    public static final String EXTRA_SOURCE_URL   = "app_source_url";

    private ActivityAddAppBinding binding;
    private FirebaseFirestore db;
    @Nullable private String appId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (AuthHelper.currentRole(this) != AuthHelper.Role.ADMIN) {
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        binding = ActivityAddAppBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        appId = getIntent().getStringExtra(EXTRA_ID);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(appId == null ? R.string.app_form_new : R.string.app_form_edit);
        }

        if (appId != null) {
            binding.etName.setText(getIntent().getStringExtra(EXTRA_NAME));
            binding.etVersion.setText(getIntent().getStringExtra(EXTRA_VERSION));
            binding.etTagline.setText(getIntent().getStringExtra(EXTRA_TAGLINE));
            binding.etDescription.setText(getIntent().getStringExtra(EXTRA_DESCRIPTION));
            binding.etIcon.setText(getIntent().getStringExtra(EXTRA_ICON));
            binding.etIconImage.setText(getIntent().getStringExtra(EXTRA_ICON_IMAGE));
            binding.etDownloadUrl.setText(getIntent().getStringExtra(EXTRA_DOWNLOAD_URL));
            binding.etSourceUrl.setText(getIntent().getStringExtra(EXTRA_SOURCE_URL));
        }

        binding.btnSave.setOnClickListener(v -> save());
    }

    private static String text(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private void save() {
        if (AuthHelper.currentRole(this) != AuthHelper.Role.ADMIN) return;

        String name = text(binding.etName);
        if (name.isEmpty()) {
            binding.nameLayout.setError(getString(R.string.app_form_name_required));
            binding.etName.requestFocus();
            return;
        }
        binding.nameLayout.setError(null);

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("version", text(binding.etVersion));
        data.put("tagline", text(binding.etTagline));
        data.put("description", text(binding.etDescription));
        data.put("icon", text(binding.etIcon));
        data.put("iconImage", text(binding.etIconImage));
        data.put("downloadUrl", text(binding.etDownloadUrl));
        data.put("sourceUrl", text(binding.etSourceUrl));

        binding.btnSave.setEnabled(false);

        if (appId != null) {
            db.collection("apps").document(appId).update(data)
                    .addOnSuccessListener(unused -> saved())
                    .addOnFailureListener(this::failed);
            return;
        }

        db.collection("apps")
                .orderBy("order", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    long next = 0;
                    if (!snap.isEmpty()) {
                        Long last = snap.getDocuments().get(0).getLong("order");
                        if (last != null) next = last + 1;
                    }
                    data.put("order", next);
                    db.collection("apps").add(data)
                            .addOnSuccessListener(ref -> saved())
                            .addOnFailureListener(this::failed);
                })
                .addOnFailureListener(this::failed);
    }

    private void saved() {
        Toast.makeText(this, R.string.app_form_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void failed(Exception e) {
        if (binding == null) return;
        binding.btnSave.setEnabled(true);
        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
