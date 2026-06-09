package com.mowtiie.faithfully.ui.activities;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.ActivityPhotoViewerBinding;

public class PhotoViewerActivity extends AppCompatActivity {

    private ActivityPhotoViewerBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityPhotoViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        setSupportActionBar(binding.toolbar);

        String imageUrl = getIntent().getStringExtra("image_url");
        String caption  = getIntent().getStringExtra("caption");

        assert caption != null;
        binding.toolbar.setTitle(caption.isEmpty() ? "Photo Viewer" : caption);

        Glide.with(this).load(imageUrl).fitCenter().into(binding.ivFull);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }
}