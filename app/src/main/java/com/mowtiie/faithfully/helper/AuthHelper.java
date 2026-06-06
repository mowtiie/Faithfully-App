package com.mowtiie.faithfully.helper;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthHelper {

    public static final String ADMIN_UID = "1V2A4PEmBZXqSe6fHZkpIg1go2g1";

    private static final String PREFS_NAME = "auth_prefs";
    private static final String KEY_GUEST_MODE  = "guest_mode_chosen";

    public static boolean isAdmin() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && ADMIN_UID.equals(user.getUid());
    }

    public static boolean isGuest(Context ctx) {
        if (isAdmin()) return false;
        return prefs(ctx).getBoolean(KEY_GUEST_MODE, false);
    }

    public static boolean hasChosenMode(Context ctx) {
        return isAdmin() || isGuest(ctx);
    }

    public static void setGuestMode(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_GUEST_MODE, enabled).apply();
    }

    public static void signOutEverything(Context ctx) {
        FirebaseAuth.getInstance().signOut();
        setGuestMode(ctx, false);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
