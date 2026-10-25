package com.mowtiie.faithfully.ui.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.ActivityAddVoiceBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.StorageException;
import com.mowtiie.faithfully.helper.VoiceFormat;
import com.mowtiie.faithfully.helper.VoiceStorageHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Records a short voice message (AAC in an .m4a, which plays everywhere), lets the sender preview it,
 * add a caption and save. Open to ADMIN and READER, like the website; the Firestore rules and the
 * Worker only allow each account to add clips for itself.
 */
public class AddVoiceActivity extends FaithfullyActivity {

    private static final String MIME_TYPE = "audio/mp4";
    private static final String EXTENSION = "m4a";

    private enum Phase { IDLE, RECORDING, REVIEW, SAVING }

    private ActivityAddVoiceBinding binding;
    private FirebaseFirestore db;

    private Phase phase = Phase.IDLE;
    private MediaRecorder recorder;
    private MediaPlayer preview;
    private File recording;
    private long startedAt;
    private double duration;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clock = new Runnable() {
        @Override
        public void run() {
            double elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000.0;
            binding.tvClock.setText(VoiceFormat.formatClock(elapsed));
            handler.postDelayed(this, 200);
        }
    };

    private final ActivityResultLauncher<String> micPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startRecording();
                } else {
                    binding.tvHint.setText(shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                            ? R.string.voice_permission_needed
                            : R.string.voice_permission_denied);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthHelper.Role role = AuthHelper.currentRole(this);
        if (role != AuthHelper.Role.ADMIN && role != AuthHelper.Role.READER) {
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        binding = ActivityAddVoiceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        recording = new File(getCacheDir(), "voice_recording." + EXTENSION);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.btnRecord.setOnClickListener(v -> {
            if (phase == Phase.IDLE) requestRecording();
            else if (phase == Phase.RECORDING) stopRecording();
        });
        binding.btnPreview.setOnClickListener(v -> togglePreview());
        binding.btnAgain.setOnClickListener(v -> {
            discardRecording();
            setPhase(Phase.IDLE);
        });
        binding.btnSave.setOnClickListener(v -> save());

        setPhase(Phase.IDLE);
    }

    private void requestRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startRecording() {
        discardRecording();

        MediaRecorder r = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new MediaRecorder(this) : new MediaRecorder();
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioChannels(1);
            r.setAudioSamplingRate(44_100);
            r.setAudioEncodingBitRate(48_000);
            r.setMaxDuration(VoiceFormat.MAX_SECONDS * 1000);
            r.setOutputFile(recording.getPath());
            r.setOnInfoListener((mr, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecording();
            });
            r.prepare();
            r.start();
        } catch (IOException | RuntimeException e) {
            r.release();
            Toast.makeText(this, R.string.voice_record_failed, Toast.LENGTH_LONG).show();
            return;
        }

        recorder = r;
        startedAt = SystemClock.elapsedRealtime();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setPhase(Phase.RECORDING);
        handler.post(clock);
    }

    private void stopRecording() {
        if (recorder == null) return;

        handler.removeCallbacks(clock);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        duration = Math.min(VoiceFormat.MAX_SECONDS, (SystemClock.elapsedRealtime() - startedAt) / 1000.0);

        boolean usable = true;
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            usable = false;
        }
        recorder.release();
        recorder = null;

        if (!usable || duration < 0.5) {
            discardRecording();
            setPhase(Phase.IDLE);
            binding.tvHint.setText(R.string.voice_too_short);
            return;
        }
        if (recording.length() > VoiceFormat.MAX_BYTES) {
            discardRecording();
            setPhase(Phase.IDLE);
            binding.tvHint.setText(R.string.voice_too_large);
            return;
        }

        setPhase(Phase.REVIEW);
    }

    private void togglePreview() {
        if (preview != null) {
            stopPreview();
            return;
        }

        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(recording.getPath());
            mp.setOnCompletionListener(m -> stopPreview());
            mp.prepare();
            mp.start();
        } catch (IOException | IllegalStateException e) {
            mp.release();
            Toast.makeText(this, R.string.voice_play_failed, Toast.LENGTH_LONG).show();
            return;
        }
        preview = mp;
        binding.btnPreview.setText(R.string.voice_stop_preview);
        binding.btnPreview.setIconResource(R.drawable.ic_stop);
    }

    private void stopPreview() {
        if (preview != null) {
            preview.release();
            preview = null;
        }
        if (binding != null) {
            binding.btnPreview.setText(R.string.voice_play_preview);
            binding.btnPreview.setIconResource(R.drawable.ic_play);
        }
    }

    private void discardRecording() {
        stopPreview();
        //noinspection ResultOfMethodCallIgnored
        recording.delete();
        duration = 0;
    }

    private void setPhase(Phase next) {
        phase = next;
        boolean saving = next == Phase.SAVING;
        boolean review = next == Phase.REVIEW || saving;

        binding.btnRecord.setVisibility(review ? View.GONE : View.VISIBLE);
        binding.reviewRow.setVisibility(review ? View.VISIBLE : View.GONE);
        binding.captionLayout.setVisibility(review ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(next == Phase.REVIEW);
        binding.btnPreview.setEnabled(!saving);
        binding.btnAgain.setEnabled(!saving);
        binding.etCaption.setEnabled(!saving);
        binding.progressBar.setVisibility(saving ? View.VISIBLE : View.GONE);
        binding.tvProgress.setVisibility(saving ? View.VISIBLE : View.GONE);

        if (next == Phase.IDLE) {
            binding.btnRecord.setIconResource(R.drawable.ic_mic);
            binding.btnRecord.setContentDescription(getString(R.string.voice_start_recording));
            binding.tvClock.setText(VoiceFormat.formatClock(0));
            binding.tvHint.setText(getString(R.string.voice_hint_idle, VoiceFormat.formatClock(VoiceFormat.MAX_SECONDS)));
        } else if (next == Phase.RECORDING) {
            binding.btnRecord.setIconResource(R.drawable.ic_stop);
            binding.btnRecord.setContentDescription(getString(R.string.voice_stop_recording));
            binding.tvHint.setText(R.string.voice_hint_recording);
        } else {
            binding.tvClock.setText(VoiceFormat.formatClock(duration));
            binding.tvHint.setText("");
        }
    }

    private void save() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !recording.isFile()) return;

        stopPreview();
        final String uid = user.getUid();
        final String caption = binding.etCaption.getText() != null ? binding.etCaption.getText().toString().trim() : "";
        binding.tvProgress.setText("Uploading...");
        setPhase(Phase.SAVING);

        new Thread(() -> {
            String key = VoiceStorageHelper.newKey(uid, EXTENSION);
            try {
                byte[] audio = readFile(recording);
                VoiceStorageHelper.upload(this, key, audio, MIME_TYPE);
                runOnUiThread(() -> saveFirestoreDoc(uid, key, audio.length, caption));
            } catch (IOException e) {
                runOnUiThread(() -> onSaveError(e));
            }
        }).start();
    }

    private void saveFirestoreDoc(String uid, String key, long size, String caption) {
        binding.tvProgress.setText("Saving...");

        Map<String, Object> data = new HashMap<>();
        data.put("audioPath", key);
        data.put("caption", caption);
        data.put("duration", Math.round(duration * 10) / 10.0);
        data.put("mimeType", MIME_TYPE);
        data.put("size", size);
        data.put("uid", uid);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("voice").add(data)
                .addOnSuccessListener(ref -> {
                    discardRecording();
                    Toast.makeText(this, "Recording saved 🎙️", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    onSaveError(e);
                    // Only the admin may delete files, so for Ali this is a quiet best effort.
                    new Thread(() -> {
                        try {
                            VoiceStorageHelper.delete(this, key);
                        } catch (IOException | SecurityException ignored) {
                        }
                    }).start();
                });
    }

    private void onSaveError(Exception e) {
        if (binding == null) return;
        setPhase(Phase.REVIEW);
        String message = e instanceof StorageException || e.getMessage() == null
                ? e.getMessage() : "Could not save the recording. Check your connection and try again.";
        Toast.makeText(this, message != null ? message : getString(R.string.voice_record_failed), Toast.LENGTH_LONG).show();
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (phase == Phase.RECORDING) stopRecording();
        stopPreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(clock);
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
            }
            recorder.release();
            recorder = null;
        }
        if (preview != null) {
            preview.release();
            preview = null;
        }
        if (recording != null) {
            //noinspection ResultOfMethodCallIgnored
            recording.delete();
        }
        binding = null;
    }
}
