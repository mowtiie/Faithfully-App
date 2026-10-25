package com.mowtiie.faithfully.ui.activities;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.data.Photo;
import com.mowtiie.faithfully.databinding.ActivityPhotoViewerBinding;
import com.mowtiie.faithfully.helper.GallerySession;
import com.mowtiie.faithfully.ui.adapters.PhotoPagerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen photos you swipe through, like the website's lightbox. The photos come from
 * {@link GallerySession}, set by GalleryActivity; opened with only an image URL it shows that one.
 */
public class PhotoViewerActivity extends FaithfullyActivity {

    public static final String EXTRA_INDEX = "index";

    private ActivityPhotoViewerBinding binding;
    private List<Photo> photos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        photos = new ArrayList<>(GallerySession.photos());
        if (photos.isEmpty()) {
            String imageUrl = getIntent().getStringExtra("image_url");
            if (imageUrl == null) {
                finish();
                return;
            }
            photos.add(new Photo("single", imageUrl, null, getIntent().getStringExtra("caption"), 0, null, null, null));
        }

        EdgeToEdge.enable(this);
        binding = ActivityPhotoViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        int start = Math.max(0, Math.min(photos.size() - 1, getIntent().getIntExtra(EXTRA_INDEX, 0)));

        binding.pager.setAdapter(new PhotoPagerAdapter(this, photos));
        binding.pager.setOffscreenPageLimit(1);
        binding.pager.setCurrentItem(start, false);
        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showInfo(position);
            }
        });

        showInfo(start);
    }

    private void showInfo(int position) {
        if (getSupportActionBar() == null) return;

        String caption = photos.get(position).getCaption();
        getSupportActionBar().setTitle(caption == null || caption.isEmpty() ? getString(R.string.viewer_title) : caption);
        getSupportActionBar().setSubtitle(photos.size() > 1
                ? getString(R.string.viewer_counter, position + 1, photos.size())
                : null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
