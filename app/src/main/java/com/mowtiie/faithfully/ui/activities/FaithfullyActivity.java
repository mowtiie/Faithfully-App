package com.mowtiie.faithfully.ui.activities;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;
import com.mowtiie.faithfully.R;

public class FaithfullyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String selectedContrast = prefs.getString("list_contrast", "low");
        switch (selectedContrast) {
            case "low":
                setTheme(R.style.Theme_Faithfully);
                break;
            case "high":
                setTheme(R.style.Theme_Faithfully_MediumContrast);
                break;
            case "medium":
            default:
                setTheme(R.style.Theme_Faithfully_HighContrast);
                break;
        }

        boolean useDynamicColor = prefs.getBoolean("switch_dynamic_color", false);
        if (useDynamicColor) {
            DynamicColors.applyToActivityIfAvailable(this);
        }

        super.onCreate(savedInstanceState);
    }
}
