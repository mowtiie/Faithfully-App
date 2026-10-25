package com.mowtiie.faithfully.ui.fragments;

import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mowtiie.faithfully.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AboutFragment extends PreferenceFragmentCompat {

    private Preference appVersion;
    private Preference appLicense;

    private int easterEggCounter;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_about, rootKey);

        appVersion = findPreference("app_version");
        appLicense = findPreference("app_license");

        try {
            PackageManager packageManager = requireContext().getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(requireContext().getPackageName(), 0);
            appVersion.setSummary(packageInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        appVersion.setOnPreferenceClickListener(preference -> {
            easterEggCounter++;
            if (easterEggCounter == 7) {
                String easterEggMessage = getString(R.string.app_easter_egg);
                Toast.makeText(requireContext(), easterEggMessage, Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        appLicense.setOnPreferenceClickListener(preference -> {
            showLicenseDialog();
            return true;
        });
    }

    private void showLicenseDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preference_app_license)
                .setMessage(readLicenseFromAssets())
                .setPositiveButton("Close", null);

        Dialog dialog = builder.create();
        dialog.show();
    }

    private String readLicenseFromAssets() {
        StringBuilder stringBuilder = new StringBuilder();
        AssetManager assetManager = requireContext().getAssets();

        try (InputStream inputStream = assetManager.open("license.txt")) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return stringBuilder.toString();
    }
}
