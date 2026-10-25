package com.mowtiie.faithfully.ui.activities;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Card;
import com.mowtiie.faithfully.databinding.ActivityReaderBinding;
import com.mowtiie.faithfully.helper.ReaderSession;
import com.mowtiie.faithfully.ui.adapters.ReaderPageAdapter;

import java.util.List;

/**
 * Full-screen letter reader, like the website's reader: swipe or use the buttons to move between the
 * letters of a chapter. The letters come from {@link ReaderSession}, set by CardsFragment.
 */
public class ReaderActivity extends FaithfullyActivity {

    public static final String EXTRA_INDEX = "index";

    private static final int PROGRESS_MAX = 1000;

    private ActivityReaderBinding binding;
    private List<Card> cards;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cards = ReaderSession.cards();
        if (cards.isEmpty()) {
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        binding = ActivityReaderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(ReaderSession.chapterTitle());
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        int start = Math.max(0, Math.min(cards.size() - 1, getIntent().getIntExtra(EXTRA_INDEX, 0)));

        binding.pager.setAdapter(new ReaderPageAdapter(cards));
        binding.pager.setCurrentItem(start, false);
        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateChrome(position);
            }
        });

        binding.btnPrev.setOnClickListener(v -> binding.pager.setCurrentItem(binding.pager.getCurrentItem() - 1, true));
        binding.btnNext.setOnClickListener(v -> {
            int position = binding.pager.getCurrentItem();
            if (position >= cards.size() - 1) finish();
            else binding.pager.setCurrentItem(position + 1, true);
        });

        updateChrome(start);
    }

    private void updateChrome(int position) {
        boolean last = position >= cards.size() - 1;

        binding.tvCounter.setText(getString(R.string.reader_counter, position + 1, cards.size()));
        binding.progress.setProgress((position + 1) * PROGRESS_MAX / cards.size());
        binding.btnPrev.setEnabled(position > 0);
        binding.btnNext.setText(last ? R.string.reader_back : R.string.reader_next);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
