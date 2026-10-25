package com.mowtiie.faithfully.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mowtiie.faithfully.R;
import com.mowtiie.faithfully.databinding.ActivityMainBinding;
import com.mowtiie.faithfully.helper.AuthHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * The single host screen: a toolbar, the navigation drawer and the NavHostFragment that shows each
 * section. Login, the add and edit forms, the letter reader and the photo viewer stay separate screens.
 */
public class MainActivity extends FaithfullyActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private AuthHelper.Role role;

    // Sections that are not fragments yet open their old screen from the drawer.
    private final Map<Integer, Class<?>> legacyScreens = new HashMap<>();

    private OnBackPressedCallback drawerBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.content, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        role = AuthHelper.currentRole(this);

        legacyScreens.put(R.id.chaptersFragment, ChaptersActivity.class);
        legacyScreens.put(R.id.galleryFragment, GalleryActivity.class);
        legacyScreens.put(R.id.voiceFragment, VoiceActivity.class);
        legacyScreens.put(R.id.settingsFragment, SettingsActivity.class);
        legacyScreens.put(R.id.aboutFragment, AboutActivity.class);

        setSupportActionBar(binding.toolbar);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = host.getNavController();
        appBarConfiguration = new AppBarConfiguration.Builder(R.id.homeFragment)
                .setOpenableLayout(binding.drawerLayout)
                .build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        setupDrawer();
    }

    private void setupDrawer() {
        View header = binding.navView.getHeaderView(0);
        TextView roleLabel = header.findViewById(R.id.tv_role);
        roleLabel.setText(role == AuthHelper.Role.ADMIN ? R.string.nav_role_admin
                : role == AuthHelper.Role.READER ? R.string.nav_role_reader
                : R.string.nav_role_guest);

        binding.navView.getMenu().findItem(R.id.action_sign_out)
                .setTitle(role == AuthHelper.Role.GUEST ? R.string.nav_sign_in : R.string.nav_sign_out);

        binding.navView.setNavigationItemSelectedListener(this::onDrawerItem);
        navController.addOnDestinationChangedListener((controller, destination, args) -> markCurrent(destination));

        // Added after the NavHostFragment so it wins over the back stack while the drawer is open.
        drawerBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, drawerBack);
        binding.drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                drawerBack.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                drawerBack.setEnabled(false);
            }
        });
    }

    private boolean onDrawerItem(@NonNull MenuItem item) {
        int id = item.getItemId();
        binding.drawerLayout.closeDrawers();

        if (id == R.id.action_sign_out) {
            confirmSignOut();
            return false;
        }

        Class<?> legacy = legacyScreens.get(id);
        if (legacy != null) {
            startActivity(new Intent(this, legacy));
            return false;
        }

        return NavigationUI.onNavDestinationSelected(item, navController);
    }

    private void markCurrent(NavDestination destination) {
        if (binding == null || binding.navView.getMenu().findItem(destination.getId()) == null) return;
        binding.navView.setCheckedItem(destination.getId());
    }

    private void confirmSignOut() {
        if (role == AuthHelper.Role.GUEST) {
            AuthHelper.setGuestMode(this, false);
            goToLogin();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Sign out")
                .setMessage("Sign out and return to the login screen?")
                .setPositiveButton("Sign out", (d, w) -> {
                    AuthHelper.signOutEverything(this);
                    goToLogin();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
