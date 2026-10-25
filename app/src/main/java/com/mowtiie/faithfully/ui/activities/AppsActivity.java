package com.mowtiie.faithfully.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.AppEntry;
import com.mowtiie.faithfully.databinding.ActivityAppsBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.DocFields;
import com.mowtiie.faithfully.helper.MockDataLoader;
import com.mowtiie.faithfully.ui.adapters.AppsAdapter;
import com.mowtiie.faithfully.ui.adapters.NoticeAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The website's Apps section. Guests, and signed-in users while the `apps` collection is empty or
 * unreadable, see the built-in list. The admin can save that list to Firestore and then add, edit,
 * move and delete apps. READER and GUEST are read-only.
 */
public class AppsActivity extends FaithfullyActivity implements AppsAdapter.OnAppActionListener {

    private static final String TAG = "AppsActivity";

    private ActivityAppsBinding binding;
    private FirebaseFirestore db;
    private AppsAdapter adapter;

    private final List<AppEntry> apps = new ArrayList<>();
    private boolean builtIn;
    private AuthHelper.Role role;
    private boolean isAdmin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAppsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        role = AuthHelper.currentRole(this);
        isAdmin = role == AuthHelper.Role.ADMIN;
        db = FirebaseFirestore.getInstance();

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        adapter = new AppsAdapter(this, apps, this);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        if (isAdmin) {
            binding.recyclerView.setAdapter(adapter);
        } else {
            binding.recyclerView.setAdapter(new ConcatAdapter(NoticeAdapter.forRole(this, role), adapter));
        }

        if (isAdmin) {
            binding.fabAdd.setOnClickListener(v -> startActivity(new Intent(this, AddAppActivity.class)));
            binding.btnImport.setOnClickListener(v -> importBuiltIn());
        } else {
            binding.fabAdd.hide();
        }

        if (role == AuthHelper.Role.GUEST) {
            showBuiltIn();
            return;
        }

        loadApps();
    }

    private void loadApps() {
        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("apps")
                .orderBy("order", Query.Direction.ASCENDING)
                .addSnapshotListener(this, (snapshots, error) -> {
                    binding.progressBar.setVisibility(View.GONE);

                    if (error != null || snapshots == null) {
                        Log.w(TAG, "Could not read the apps collection", error);
                        showBuiltIn();
                        return;
                    }

                    List<AppEntry> loaded = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        loaded.add(new AppEntry(
                                doc.getId(),
                                DocFields.string(doc, "name"),
                                DocFields.string(doc, "tagline"),
                                DocFields.string(doc, "description"),
                                DocFields.string(doc, "version"),
                                DocFields.string(doc, "icon"),
                                DocFields.string(doc, "iconImage"),
                                DocFields.string(doc, "downloadUrl"),
                                DocFields.string(doc, "sourceUrl"),
                                DocFields.number(doc, "order", loaded.size())
                        ));
                    }

                    if (loaded.isEmpty()) {
                        showBuiltIn();
                        return;
                    }

                    builtIn = false;
                    apps.clear();
                    apps.addAll(loaded);
                    render();
                });
    }

    private void showBuiltIn() {
        builtIn = true;
        apps.clear();
        apps.addAll(MockDataLoader.getApps(this));
        render();
    }

    private void render() {
        boolean canEdit = isAdmin && !builtIn;

        adapter.setEditable(canEdit);
        adapter.notifyDataSetChanged();

        if (canEdit) binding.fabAdd.show();
        else binding.fabAdd.hide();

        binding.importRow.setVisibility(isAdmin && builtIn ? View.VISIBLE : View.GONE);
        binding.tvEmpty.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // The built-in list becomes real documents, in order, so it can be edited like any other app.
    private void importBuiltIn() {
        if (!isAdmin) return;

        WriteBatch batch = db.batch();
        List<AppEntry> builtInApps = MockDataLoader.getApps(this);
        for (int i = 0; i < builtInApps.size(); i++) {
            AppEntry app = builtInApps.get(i);
            Map<String, Object> data = new HashMap<>();
            data.put("name", app.getName());
            data.put("tagline", app.getTagline());
            data.put("description", app.getDescription());
            data.put("version", app.getVersion());
            data.put("icon", app.getIcon());
            data.put("iconImage", app.getIconImage());
            data.put("downloadUrl", app.getDownloadUrl());
            data.put("sourceUrl", app.getSourceUrl());
            data.put("order", (long) i);
            batch.set(db.collection("apps").document(), data);
        }

        binding.btnImport.setEnabled(false);
        batch.commit()
                .addOnSuccessListener(unused -> {
                    binding.btnImport.setEnabled(true);
                    Toast.makeText(this, R.string.apps_saved, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    binding.btnImport.setEnabled(true);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public void onEdit(AppEntry app) {
        if (!isAdmin || app.getId() == null) return;

        Intent intent = new Intent(this, AddAppActivity.class);
        intent.putExtra(AddAppActivity.EXTRA_ID, app.getId());
        intent.putExtra(AddAppActivity.EXTRA_NAME, app.getName());
        intent.putExtra(AddAppActivity.EXTRA_TAGLINE, app.getTagline());
        intent.putExtra(AddAppActivity.EXTRA_DESCRIPTION, app.getDescription());
        intent.putExtra(AddAppActivity.EXTRA_VERSION, app.getVersion());
        intent.putExtra(AddAppActivity.EXTRA_ICON, app.getIcon());
        intent.putExtra(AddAppActivity.EXTRA_ICON_IMAGE, app.getIconImage());
        intent.putExtra(AddAppActivity.EXTRA_DOWNLOAD_URL, app.getDownloadUrl());
        intent.putExtra(AddAppActivity.EXTRA_SOURCE_URL, app.getSourceUrl());
        startActivity(intent);
    }

    @Override
    public void onDelete(AppEntry app) {
        if (!isAdmin || app.getId() == null) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.apps_delete_title)
                .setMessage(getString(R.string.apps_delete_message, app.getName()))
                .setPositiveButton("Delete", (d, w) -> db.collection("apps").document(app.getId())
                        .delete()
                        .addOnSuccessListener(unused -> Toast.makeText(this, R.string.apps_deleted, Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onMove(AppEntry app, int delta) {
        if (!isAdmin || builtIn) return;

        int from = apps.indexOf(app);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= apps.size()) return;

        Collections.swap(apps, from, to);
        adapter.notifyDataSetChanged();

        WriteBatch batch = db.batch();
        for (int i = 0; i < apps.size(); i++) {
            String id = apps.get(i).getId();
            if (id != null) batch.update(db.collection("apps").document(id), "order", (long) i);
        }
        batch.commit().addOnFailureListener(e ->
                Toast.makeText(this, "Failed to save order: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
