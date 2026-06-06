package com.mowtiie.faithfully.ui.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Chapter;
import com.mowtiie.faithfully.databinding.ActivityAddChapterBinding;
import com.mowtiie.faithfully.helper.ChapterDbHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AddChapterActivity extends AppCompatActivity {

    private ActivityAddChapterBinding binding;
    private FirebaseFirestore db;
    private ChapterDbHelper dbHelper;

    private String  editChapterId = null;
    private long editChapterOrder = -1;
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAddChapterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        dbHelper = ChapterDbHelper.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.containsKey("chapter_id")) {
            isEditMode = true;
            editChapterId = extras.getString("chapter_id");
            editChapterOrder = extras.getLong("chapter_order");

            binding.toolbar.setTitle("Edit Chapter");
            binding.etTitle.setText(extras.getString("chapter_title"));
            binding.etDescription.setText(extras.getString("chapter_description"));
            binding.btnSave.setText("Save Changes");
        } else {
            binding.toolbar.setTitle("New Chapter");
        }

        binding.btnSave.setOnClickListener(v -> validateAndSave());
    }

    private void validateAndSave() {
        String title = Objects.requireNonNull(binding.etTitle.getText()).toString().trim();
        String description = Objects.requireNonNull(binding.etDescription.getText()).toString().trim();

        if (TextUtils.isEmpty(title)) {
            binding.etTitle.setError("This field is required");
            binding.etTitle.requestFocus();
            return;
        }

        setLoading(true);

        if (isEditMode) {
            updateChapter(title, description);
        } else {
            addChapter(title, description);
        }
    }

    private void addChapter(String title, String description) {
        db.collection("chapters")
                .orderBy("order", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    long nextOrder = 0;
                    if (!snap.isEmpty()) {
                        Long last = snap.getDocuments().get(0).getLong("order");
                        if (last != null) nextOrder = last + 1;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("title",       title);
                    data.put("description", description.isEmpty() ? null : description);
                    data.put("order",       nextOrder);

                    final long order = nextOrder;
                    db.collection("chapters")
                            .add(data)
                            .addOnSuccessListener(ref -> {
                                dbHelper.upsert(new Chapter(
                                        ref.getId(), title,
                                        description.isEmpty() ? null : description,
                                        order));
                                setLoading(false);
                                Toast.makeText(this, "Chapter added!", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateChapter(String title, String description) {
        Map<String, Object> data = new HashMap<>();
        data.put("title",       title);
        data.put("description", description.isEmpty() ? null : description);
        data.put("order",       editChapterOrder);

        db.collection("chapters")
                .document(editChapterId)
                .update(data)
                .addOnSuccessListener(unused -> {
                    dbHelper.upsert(new Chapter(
                            editChapterId, title,
                            description.isEmpty() ? null : description,
                            editChapterOrder));
                    setLoading(false);
                    Toast.makeText(this, "Chapter updated!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!loading);
        binding.etTitle.setEnabled(!loading);
        binding.etDescription.setEnabled(!loading);
    }
}