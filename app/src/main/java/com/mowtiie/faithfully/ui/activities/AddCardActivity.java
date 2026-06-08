package com.mowtiie.faithfully.ui.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.ActivityAddCardBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

public class AddCardActivity extends FaithfullyActivity {

    private ActivityAddCardBinding binding;
    private FirebaseFirestore db;

    private String editCardId = null;
    private long editOrder  = -1;
    private boolean isEditMode = false;
    private String chapterId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAddCardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        Bundle extras = getIntent().getExtras();
        chapterId = extras != null ? extras.getString("chapter_id") : null;

        if (extras != null && extras.containsKey("card_id")) {
            isEditMode = true;
            editCardId = extras.getString("card_id");
            editOrder  = extras.getLong("card_order");

            binding.toolbar.setTitle("Edit Card");
            binding.etTitle.setText(extras.getString("card_title"));
            binding.etMessage.setText(extras.getString("card_message"));
            binding.etDate.setText(extras.getString("card_date_label"));
            binding.btnSave.setText("Save Changes");
        } else {
            String chapterTitle = extras != null ? extras.getString("chapter_title") : "";
            setTitle("New Card");
            binding.toolbar.setSubtitle(chapterTitle);
            binding.toolbar.setSubtitleCentered(true);
        }

        binding.etDate.setFocusable(false);
        binding.etDate.setOnClickListener(v -> showDatePicker());
        binding.btnSave.setOnClickListener(v -> validateAndSave());
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        String existing = Objects.requireNonNull(binding.etDate.getText()).toString().trim();

        if (!existing.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = sdf.parse(existing);
                if (d != null) cal.setTime(d);
            } catch (ParseException ignored) {
                // do nothing
            }
        }

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(cal.getTimeInMillis())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar selectedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            selectedCal.setTimeInMillis(selection);

            SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));

            binding.etDate.setText(format.format(selectedCal.getTime()));
        });

        datePicker.show(getSupportFragmentManager(), "MATERIAL_DATE_PICKER");
    }

    private void validateAndSave() {
        String title = binding.etTitle.getText().toString().trim();
        String message = binding.etMessage.getText().toString().trim();
        String dateLabel = binding.etDate.getText().toString().trim();

        if (title.isEmpty()) {
            binding.etTitle.setError("This field is required");
            binding.etTitle.requestFocus();
            return;
        }

        if (message.isEmpty()) {
            binding.etMessage.setError("This field is required");
            binding.etMessage.requestFocus();
            return;
        }

        if (dateLabel.isEmpty()) {
            binding.etDate.setError("This field is required");
            binding.etDate.requestFocus();
            return;
        }

        Date parsed;
        try {
            parsed = new SimpleDateFormat("MM/dd/yyyy", Locale.US).parse(dateLabel);
        } catch (ParseException e) {
            binding.etDate.setError("Invalid date");
            return;
        }

        if (parsed == null) {
            binding.etDate.setError("Invalid date"); return;
        }

        setLoading(true);
        Timestamp timestamp = new Timestamp(parsed);

        if (isEditMode) {
            updateCard(title, message, dateLabel, timestamp);
        } else {
            addCard(title, message, dateLabel, timestamp);
        }
    }

    private void addCard(String title, String message, String dateLabel, Timestamp timestamp) {
        db.collection("cards")
                .whereEqualTo("chapterId", chapterId)
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
                    data.put("title",     title);
                    data.put("message",   message);
                    data.put("dateLabel", dateLabel);
                    data.put("date",      timestamp);
                    data.put("order",     nextOrder);
                    data.put("chapterId", chapterId);

                    db.collection("cards").add(data)
                            .addOnSuccessListener(ref -> {
                                setLoading(false);
                                Toast.makeText(this, "Card added!", Toast.LENGTH_SHORT).show();
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

    private void updateCard(String title, String message, String dateLabel, Timestamp timestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put("title",     title);
        data.put("message",   message);
        data.put("dateLabel", dateLabel);
        data.put("date",      timestamp);
        data.put("order",     editOrder);
        data.put("chapterId", chapterId);

        db.collection("cards").document(editCardId)
                .update(data)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    Toast.makeText(this, "Card updated!", Toast.LENGTH_SHORT).show();
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
        binding.etMessage.setEnabled(!loading);
        binding.etDate.setEnabled(!loading);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }
}