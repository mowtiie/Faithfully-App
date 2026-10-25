package com.mowtiie.faithfully.ui.fragments;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.mowtiie.faithfully.R;

public class SettingsFragment extends PreferenceFragmentCompat {

    private ListPreference listTheme;
    private ListPreference listContrast;

    private SwitchPreferenceCompat switchDynamicColor;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_settings, rootKey);

        listTheme = findPreference("list_theme");
        listContrast = findPreference("list_contrast");
        switchDynamicColor = findPreference("switch_dynamic_color");

        if (listTheme != null) {
            listTheme.setOnPreferenceChangeListener((preference, newValue) -> {
                String themeOption = (String) newValue;
                applyTheme(themeOption);
                return true;
            });
        }

        if (listContrast != null) {
            listContrast.setOnPreferenceChangeListener((preference, newValue) -> {
                requireActivity().recreate();
                return true;
            });
        }

        if (switchDynamicColor != null) {
            switchDynamicColor.setOnPreferenceChangeListener((preference, newValue) -> {
                requireActivity().recreate();
                return true;
            });
        }
    }

    private void applyTheme(String selectedTheme) {
        switch (selectedTheme) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "battery_saving":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
                break;
            case "system_default":
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
