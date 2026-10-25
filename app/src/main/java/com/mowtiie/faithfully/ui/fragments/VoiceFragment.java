package com.mowtiie.faithfully.ui.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.VoiceClip;
import com.mowtiie.faithfully.databinding.FragmentVoiceBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.DocFields;
import com.mowtiie.faithfully.helper.MockDataLoader;
import com.mowtiie.faithfully.helper.VoiceStorageHelper;
import com.mowtiie.faithfully.ui.activities.AddVoiceActivity;
import com.mowtiie.faithfully.ui.adapters.NoticeAdapter;
import com.mowtiie.faithfully.ui.adapters.VoiceAdapter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The website's Voice section. ADMIN and READER read and add recordings; only ADMIN edits or deletes.
 * GUEST sees sample cards with playback off. Clips have no public address: playback downloads the
 * file through the Worker into the app cache and plays that. Filter and sort live in the top app bar.
 */
public class VoiceFragment extends Fragment implements VoiceAdapter.OnVoiceActionListener, MenuProvider {

    private static final long TICK_MS = 250;
    private static final String ALL = "all";
    private static final int GROUP_FILTER = 1;

    private enum Sort { NEWEST, OLDEST, LONGEST, SHORTEST }

    private FragmentVoiceBinding binding;
    private FirebaseFirestore db;
    private ListenerRegistration playersRegistration;
    private ListenerRegistration voiceRegistration;

    private VoiceAdapter adapter;
    private final List<VoiceClip> allClips = new ArrayList<>();
    private final List<VoiceClip> clips = new ArrayList<>();
    private final Map<String, String> players = new HashMap<>();
    private final List<String> filterUids = new ArrayList<>();
    private String filterUid = ALL;
    private Sort sort = Sort.NEWEST;
    private AuthHelper.Role role;

    private MediaPlayer player;
    private boolean prepared;
    private String playingId;
    private int loadToken;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            publishProgress();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentVoiceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context ctx = requireContext();
        role = AuthHelper.currentRole(ctx);
        db = FirebaseFirestore.getInstance();

        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        if (role == AuthHelper.Role.GUEST) {
            binding.fabAdd.hide();
        } else {
            binding.fabAdd.setOnClickListener(v -> startActivity(new Intent(ctx, AddVoiceActivity.class)));
        }

        adapter = new VoiceAdapter(ctx, clips, players, this, role);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(ctx));
        if (binding.recyclerView.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) binding.recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        if (role == AuthHelper.Role.GUEST) {
            binding.recyclerView.setAdapter(new ConcatAdapter(NoticeAdapter.forRole(ctx, role), adapter));
            showMock();
            return;
        }

        binding.recyclerView.setAdapter(adapter);
        loadVoice();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (playersRegistration != null) {
            playersRegistration.remove();
            playersRegistration = null;
        }
        if (voiceRegistration != null) {
            voiceRegistration.remove();
            voiceRegistration = null;
        }
        releasePlayer();
        loadToken++;
        playingId = null;
        binding = null;
    }

    private void showMock() {
        allClips.clear();
        allClips.addAll(MockDataLoader.getVoice(requireContext()));
        players.clear();
        players.putAll(MockDataLoader.getVoicePlayers(requireContext()));
        applyView();
    }

    private void loadVoice() {
        binding.progressBar.setVisibility(View.VISIBLE);

        // The emoji for each account comes from the same document the website's Draw board uses.
        playersRegistration = db.collection("settings").document("draw")
                .addSnapshotListener((snap, error) -> {
                    if (binding == null || error != null || snap == null) return;
                    players.clear();
                    Object raw = snap.get("players");
                    if (raw instanceof Map) {
                        for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                            if (e.getKey() instanceof String && e.getValue() instanceof String) {
                                players.put((String) e.getKey(), (String) e.getValue());
                            }
                        }
                    }
                    applyView();
                });

        voiceRegistration = db.collection("voice")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);

                    if (error != null) {
                        Toast.makeText(requireContext(), "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }

                    allClips.clear();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            allClips.add(new VoiceClip(
                                    doc.getId(),
                                    DocFields.string(doc, "audioPath"),
                                    DocFields.string(doc, "caption"),
                                    DocFields.decimal(doc, "duration", 0),
                                    DocFields.string(doc, "uid"),
                                    DocFields.timestamp(doc, "createdAt")
                            ));
                        }
                    }

                    if (playingId != null && !containsClip(playingId)) stopPlayback();
                    applyView();
                });
    }

    private boolean containsClip(String id) {
        for (VoiceClip clip : allClips) if (clip.getId().equals(id)) return true;
        return false;
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_voice, menu);
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        menu.findItem(sortItemId()).setChecked(true);

        SubMenu filterMenu = menu.findItem(R.id.menu_filter).getSubMenu();
        if (filterMenu == null) return;
        filterMenu.clear();
        filterUids.clear();
        filterUids.add(ALL);
        filterUids.addAll(senders());

        for (int i = 0; i < filterUids.size(); i++) {
            String uid = filterUids.get(i);
            String title = ALL.equals(uid)
                    ? getString(R.string.voice_filter_all, allClips.size())
                    : getString(R.string.voice_filter_sender, senderLabel(uid), countFrom(uid));
            filterMenu.add(GROUP_FILTER, i, i, title)
                    .setCheckable(true)
                    .setChecked(uid.equals(filterUid));
        }
        filterMenu.setGroupCheckable(GROUP_FILTER, true, true);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        if (item.getGroupId() == GROUP_FILTER) {
            if (item.getItemId() < filterUids.size()) {
                filterUid = filterUids.get(item.getItemId());
                applyView();
            }
            return true;
        }

        int id = item.getItemId();
        if (id == R.id.sort_newest || id == R.id.sort_oldest || id == R.id.sort_longest || id == R.id.sort_shortest) {
            sort = sortFor(id);
            applyView();
            return true;
        }
        return false;
    }

    private int sortItemId() {
        if (sort == Sort.OLDEST)   return R.id.sort_oldest;
        if (sort == Sort.LONGEST)  return R.id.sort_longest;
        if (sort == Sort.SHORTEST) return R.id.sort_shortest;
        return R.id.sort_newest;
    }

    private Sort sortFor(int itemId) {
        if (itemId == R.id.sort_oldest)   return Sort.OLDEST;
        if (itemId == R.id.sort_longest)  return Sort.LONGEST;
        if (itemId == R.id.sort_shortest) return Sort.SHORTEST;
        return Sort.NEWEST;
    }

    // A clip still waiting for its server timestamp is the newest; sample clips have no date and keep their order.
    private long createdMillis(VoiceClip clip) {
        if (clip.getCreatedAt() != null) return clip.getCreatedAt().toDate().getTime();
        return role == AuthHelper.Role.GUEST ? 0 : Long.MAX_VALUE;
    }

    private Comparator<VoiceClip> comparator() {
        switch (sort) {
            case OLDEST:   return (a, b) -> Long.compare(createdMillis(a), createdMillis(b));
            case LONGEST:  return (a, b) -> Double.compare(b.getDuration(), a.getDuration());
            case SHORTEST: return (a, b) -> Double.compare(a.getDuration(), b.getDuration());
            default:       return (a, b) -> Long.compare(createdMillis(b), createdMillis(a));
        }
    }

    // Both accounts always get an entry, even before they have sent anything.
    private List<String> senders() {
        Set<String> uids = new LinkedHashSet<>();
        if (role != AuthHelper.Role.GUEST) {
            uids.add(AuthHelper.ADMIN_UID);
            uids.add(AuthHelper.ALI_UID);
        }
        uids.addAll(new TreeSet<>(players.keySet()));
        for (VoiceClip clip : allClips) if (clip.getUid() != null) uids.add(clip.getUid());
        return new ArrayList<>(uids);
    }

    private String senderLabel(String uid) {
        String emoji = players.get(uid);
        if (emoji != null) return emoji;
        if (role == AuthHelper.Role.GUEST) return "🎙️";
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return getString(user != null && uid.equals(user.getUid()) ? R.string.voice_chip_me : R.string.voice_chip_them);
    }

    private int countFrom(String uid) {
        int count = 0;
        for (VoiceClip clip : allClips) if (uid.equals(clip.getUid())) count++;
        return count;
    }

    private void applyView() {
        if (!ALL.equals(filterUid) && !senders().contains(filterUid)) filterUid = ALL;

        clips.clear();
        for (VoiceClip clip : allClips) {
            if (ALL.equals(filterUid) || filterUid.equals(clip.getUid())) clips.add(clip);
        }
        Collections.sort(clips, comparator());
        adapter.notifyDataSetChanged();

        binding.tvEmpty.setText(allClips.isEmpty() ? R.string.voice_empty : R.string.voice_none_from_sender);
        binding.tvEmpty.setVisibility(clips.isEmpty() ? View.VISIBLE : View.GONE);
        requireActivity().invalidateOptionsMenu();
    }

    @Override
    public void onPlayPause(VoiceClip clip) {
        if (clip.getAudioPath() == null) return;

        // Still downloading: ignore taps until it starts or fails.
        if (clip.getId().equals(playingId) && player == null) return;

        if (clip.getId().equals(playingId) && player != null) {
            if (!prepared) return;
            if (player.isPlaying()) {
                player.pause();
                handler.removeCallbacks(ticker);
                adapter.setPlayback(playingId, VoiceAdapter.State.PAUSED, player.getCurrentPosition(), player.getDuration(), null);
            } else {
                player.start();
                handler.post(ticker);
                adapter.setPlayback(playingId, VoiceAdapter.State.PLAYING, player.getCurrentPosition(), player.getDuration(), null);
            }
            return;
        }

        startPlayback(clip);
    }

    private void startPlayback(VoiceClip clip) {
        stopPlayback();
        final int token = ++loadToken;
        final Context ctx = requireContext();
        final Activity activity = requireActivity();
        playingId = clip.getId();
        adapter.setPlayback(playingId, VoiceAdapter.State.LOADING, 0, 0, null);

        new Thread(() -> {
            try {
                File file = VoiceStorageHelper.download(ctx, clip.getAudioPath());
                activity.runOnUiThread(() -> {
                    if (token == loadToken && binding != null) prepare(clip, file, token);
                });
            } catch (IOException | SecurityException e) {
                activity.runOnUiThread(() -> {
                    if (token == loadToken && binding != null) fail(clip.getId(), e.getMessage());
                });
            }
        }).start();
    }

    private void prepare(VoiceClip clip, File file, int token) {
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        prepared = false;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(file.getPath());
            mp.setOnPreparedListener(m -> {
                if (token != loadToken) return;
                prepared = true;
                m.start();
                adapter.setPlayback(clip.getId(), VoiceAdapter.State.PLAYING, 0, m.getDuration(), null);
                handler.post(ticker);
            });
            mp.setOnCompletionListener(m -> {
                if (token == loadToken) stopPlayback();
            });
            mp.setOnErrorListener((m, what, extra) -> {
                if (token == loadToken) fail(clip.getId(), getString(R.string.voice_play_failed));
                return true;
            });
            mp.prepareAsync();
        } catch (IOException | IllegalStateException e) {
            fail(clip.getId(), getString(R.string.voice_play_failed));
        }
    }

    private void publishProgress() {
        if (player == null || !prepared || playingId == null || !player.isPlaying()) return;
        adapter.setPlayback(playingId, VoiceAdapter.State.PLAYING, player.getCurrentPosition(), player.getDuration(), null);
    }

    private void fail(String id, String message) {
        releasePlayer();
        loadToken++;
        playingId = null;
        adapter.setPlayback(id, VoiceAdapter.State.ERROR, 0, 0,
                message != null ? message : getString(R.string.voice_play_failed));
    }

    private void stopPlayback() {
        releasePlayer();
        loadToken++;
        playingId = null;
        adapter.setPlayback(null, VoiceAdapter.State.IDLE, 0, 0, null);
    }

    private void releasePlayer() {
        handler.removeCallbacks(ticker);
        if (player != null) {
            player.release();
            player = null;
        }
        prepared = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
            handler.removeCallbacks(ticker);
            adapter.setPlayback(playingId, VoiceAdapter.State.PAUSED, player.getCurrentPosition(), player.getDuration(), null);
        }
    }

    @Override
    public void onEditCaption(VoiceClip clip) {
        if (role != AuthHelper.Role.ADMIN) return;

        Context ctx = requireContext();
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_edit_caption, null);
        TextInputEditText input = view.findViewById(R.id.input_caption);
        input.setText(clip.getCaption());
        input.setSelection(clip.getCaption().length());

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle("Edit Caption")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String caption = Objects.requireNonNull(input.getText()).toString().trim();
            db.collection("voice").document(clip.getId())
                    .update("caption", caption)
                    .addOnSuccessListener(unused -> Toast.makeText(ctx, "Caption updated", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(ctx, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            dialog.dismiss();
        }));
        dialog.show();
    }

    @Override
    public void onDelete(VoiceClip clip) {
        if (role != AuthHelper.Role.ADMIN) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.voice_delete_title)
                .setMessage(R.string.voice_delete_message)
                .setPositiveButton("Delete", (d, w) -> deleteClip(clip))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // The file goes first so a failure leaves the document in place and the delete can be retried.
    private void deleteClip(VoiceClip clip) {
        if (role != AuthHelper.Role.ADMIN) return;

        Context ctx = requireContext();
        Activity activity = requireActivity();
        new Thread(() -> {
            try {
                if (clip.getAudioPath() != null) VoiceStorageHelper.delete(ctx, clip.getAudioPath());
            } catch (IOException e) {
                activity.runOnUiThread(() -> Toast.makeText(ctx, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }

            activity.runOnUiThread(() -> db.collection("voice").document(clip.getId())
                    .delete()
                    .addOnSuccessListener(unused -> Toast.makeText(ctx, "Recording deleted", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(ctx, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()));
        }).start();
    }
}
