package com.mowtiie.faithfully.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.ActivitySettingsBinding;
import com.mowtiie.faithfully.helper.AuthHelper;
import com.mowtiie.faithfully.helper.BackupManager;

public class SettingsActivity extends FaithfullyActivity {

    private ActivitySettingsBinding binding;
    private BackupManager backupManager;

    private final ActivityResultLauncher<Intent> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && isAdmin()) backupManager.export(uri);
                }
            });

    private final ActivityResultLauncher<Intent> importLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && isAdmin()) backupManager.confirmAndRestore(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backupManager = new BackupManager(this);
        EdgeToEdge.enable(this);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backupManager.shutdown();
    }

    private boolean isAdmin() {
        return AuthHelper.currentRole(this) == AuthHelper.Role.ADMIN;
    }

    private void launchExportPicker() {
        if (!isAdmin()) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, BackupManager.suggestedFileName());
        exportLauncher.launch(intent);
    }

    private void launchImportPicker() {
        if (!isAdmin()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{ "application/zip", "application/json" });
        importLauncher.launch(intent);
    }

    private void confirmClearAll() {
        if (!isAdmin()) return;
        backupManager.confirmClearAll();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private ListPreference listTheme;
        private ListPreference listContrast;

        private SwitchPreferenceCompat switchDynamicColor;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_settings, rootKey);

            setupBackup();

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

        // Backup & Restore and Clear all are admin only; everyone else never sees them.
        private void setupBackup() {
            SettingsActivity host = (SettingsActivity) requireActivity();
            boolean admin = host.isAdmin();

            PreferenceCategory backup = findPreference("category_backup");
            PreferenceCategory danger = findPreference("category_danger_zone");
            if (backup != null) backup.setVisible(admin);
            if (danger != null) danger.setVisible(admin);
            if (!admin) return;

            Preference export = findPreference("export");
            Preference restore = findPreference("restore");
            Preference clearAll = findPreference("clear_all");

            if (export != null) export.setOnPreferenceClickListener(p -> { host.launchExportPicker(); return true; });
            if (restore != null) restore.setOnPreferenceClickListener(p -> { host.launchImportPicker(); return true; });
            if (clearAll != null) clearAll.setOnPreferenceClickListener(p -> { host.confirmClearAll(); return true; });
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
}