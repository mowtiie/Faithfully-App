package com.mowtiie.faithfully.ui.fragments;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.FragmentHomeBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.AuthHelper.Role;
import com.mowtiie.faithfully.helper.HomeContentResolver;
import com.mowtiie.faithfully.helper.MockDataLoader;

import java.util.Date;
import java.util.Locale;

/**
 * Home, mirroring the website's js/home.js: a welcome with the days-together counter and the countdown.
 * ADMIN and READER read settings/home; GUEST sees the built-in sample content and never touches
 * Firestore. Read-only for every role.
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final String PREFS = "home_prefs";
    private static final String KEY_GUEST_FIRST_VISIT = "guest_first_visit";

    private FragmentHomeBinding binding;
    private Role role;
    private boolean signedIn;

    private HomeContentResolver.Text defaults;
    private HomeContentResolver.Settings settings;
    private boolean settingsResolved;
    private Date guestStart;

    private HomeContentResolver.Content content;
    private ListenerRegistration registration;
    private ObjectAnimator skeletonPulse;
    private ObjectAnimator finishPulse;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        role = AuthHelper.currentRole(requireContext());
        signedIn = role != Role.GUEST;
        settingsResolved = false;
        settings = null;
        defaults = MockDataLoader.getHomeDefaults(requireContext());
        if (!signedIn) guestStart = guestFirstVisit();

        if (role != Role.ADMIN && !isBannerDismissed()) {
            binding.tvBanner.setText(role == Role.READER ? R.string.notice_reader : R.string.notice_guest);
            binding.bannerCard.setVisibility(View.VISIBLE);
            binding.btnBannerDismiss.setOnClickListener(v -> {
                prefs().edit().putBoolean(bannerKey(), true).apply();
                binding.bannerCard.setVisibility(View.GONE);
            });
        }

        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (signedIn) watchSettings();
        handler.post(ticker);
        if (signedIn && !settingsResolved) startSkeletonPulse();
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(ticker);
        stopAnimator(skeletonPulse);
        skeletonPulse = null;
        stopAnimator(finishPulse);
        finishPulse = null;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void watchSettings() {
        if (registration != null) return;
        registration = FirebaseFirestore.getInstance()
                .collection("settings").document("home")
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        Log.e(TAG, "settings/home failed", error);
                        settings = null;
                    } else {
                        settings = snap != null && snap.exists() ? toSettings(snap) : null;
                        if (settings == null || settings.anniversaryStart == null) {
                            Log.w(TAG, "settings/home has no usable anniversaryStart");
                        }
                    }
                    settingsResolved = true;
                    render();
                });
    }

    private static HomeContentResolver.Settings toSettings(DocumentSnapshot doc) {
        HomeContentResolver.Settings s = new HomeContentResolver.Settings();
        s.title            = doc.getString("title");
        s.subtitle         = doc.getString("subtitle");
        s.countdownLabel   = doc.getString("countdownLabel");
        s.countdownFinish  = doc.getString("countdownFinish");
        s.anniversaryLabel = doc.getString("anniversaryLabel");
        s.anniversaryUnit  = doc.getString("anniversaryUnit");
        s.countdownTarget  = toDate(doc.getTimestamp("countdownTarget"));
        s.anniversaryStart = toDate(doc.getTimestamp("anniversaryStart"));
        return s;
    }

    private static Date toDate(Timestamp ts) {
        return ts != null ? ts.toDate() : null;
    }

    /** Guests count from the first time they opened Home, like the website's demoFirstVisit. */
    private Date guestFirstVisit() {
        SharedPreferences prefs = prefs();
        long stored = prefs.getLong(KEY_GUEST_FIRST_VISIT, 0);
        if (stored == 0) {
            stored = System.currentTimeMillis();
            prefs.edit().putLong(KEY_GUEST_FIRST_VISIT, stored).apply();
        }
        return new Date(stored);
    }

    private void render() {
        if (binding == null || defaults == null) return;

        // Signed-in users wait for the first snapshot so sample text doesn't flash before real text.
        boolean pending = signedIn && !settingsResolved;
        binding.contentColumn.setVisibility(pending ? View.GONE : View.VISIBLE);
        binding.skeleton.setVisibility(pending ? View.VISIBLE : View.GONE);
        if (pending) {
            startSkeletonPulse();
        } else {
            stopAnimator(skeletonPulse);
            skeletonPulse = null;
        }

        content = HomeContentResolver.resolve(defaults, signedIn, settings, guestStart, new Date());

        binding.tvTitle.setText(content.text.title);
        binding.tvSubtitle.setText(TextUtils.join("\n", HomeContentResolver.subtitleLines(content.text.subtitle)));
        binding.tvCountdownLabel.setText(content.text.countdownLabel);
        binding.tvCountdownFinish.setText(content.text.countdownFinish);
        binding.tvAnniversaryLabel.setText(content.text.anniversaryLabel);
        binding.tvAnniversaryUnit.setText(content.text.anniversaryUnit);

        tick();
    }

    private void tick() {
        if (binding == null || content == null) return;
        Date now = new Date();

        HomeContentResolver.CountdownParts parts = HomeContentResolver.countdownParts(content.countdownTarget, now);
        if (parts == null) {
            binding.countdownBlocks.setVisibility(View.GONE);
            binding.tvCountdownFinish.setVisibility(View.VISIBLE);
            startFinishPulse();
        } else {
            binding.countdownBlocks.setVisibility(View.VISIBLE);
            binding.tvCountdownFinish.setVisibility(View.GONE);
            stopAnimator(finishPulse);
            finishPulse = null;
            binding.tvDays.setText(pad(parts.days));
            binding.tvHours.setText(pad(parts.hours));
            binding.tvMinutes.setText(pad(parts.minutes));
            binding.tvSeconds.setText(pad(parts.seconds));
            binding.countdownBlocks.setContentDescription(getString(R.string.home_countdown_desc,
                    parts.days, parts.hours, parts.minutes, parts.seconds));
        }

        long days = HomeContentResolver.daysSince(content.anniversaryStart, now);
        binding.tvAnniversaryDays.setText(days < 0 ? "--" : String.valueOf(days));
    }

    private String bannerKey() {
        return "banner_dismissed_" + role.name();
    }

    private boolean isBannerDismissed() {
        return prefs().getBoolean(bannerKey(), false);
    }

    private void startSkeletonPulse() {
        if (binding == null || skeletonPulse != null || !ValueAnimator.areAnimatorsEnabled()) return;
        skeletonPulse = ObjectAnimator.ofFloat(binding.skeleton, View.ALPHA, 1f, 0.45f);
        skeletonPulse.setDuration(800);
        skeletonPulse.setRepeatMode(ValueAnimator.REVERSE);
        skeletonPulse.setRepeatCount(ValueAnimator.INFINITE);
        skeletonPulse.start();
    }

    /** A gentle heartbeat on the "countdown finished" message. Started once; tick() runs every second. */
    private void startFinishPulse() {
        if (binding == null || finishPulse != null || !ValueAnimator.areAnimatorsEnabled()) return;
        finishPulse = ObjectAnimator.ofPropertyValuesHolder(binding.tvCountdownFinish,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f));
        finishPulse.setDuration(700);
        finishPulse.setRepeatMode(ValueAnimator.REVERSE);
        finishPulse.setRepeatCount(ValueAnimator.INFINITE);
        finishPulse.start();
    }

    private static void stopAnimator(ObjectAnimator animator) {
        if (animator != null) animator.cancel();
    }

    private static String pad(long value) {
        return String.format(Locale.getDefault(), "%02d", value);
    }
}
