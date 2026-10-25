package com.mowtiie.faithfully.helper;


import android.content.Context;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.mowtiie.faithfully.data.AppEntry;
import com.mowtiie.faithfully.data.Card;
import com.mowtiie.faithfully.data.Chapter;
import com.mowtiie.faithfully.data.Photo;
import com.mowtiie.faithfully.data.VoiceClip;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MockDataLoader {

    private static final String TAG = "MockDataLoader";
    private static final String ASSET_PATH = "mock_data.json";

    private static List<Chapter> cachedChapters;
    private static List<Card> cachedCards;
    private static List<Photo> cachedPhotos;
    private static List<VoiceClip> cachedVoice;
    private static List<AppEntry> cachedApps;
    private static Map<String, String> cachedVoicePlayers;
    private static HomeContentResolver.Text cachedHome = HomeContentResolver.Text.fallback();
    private static boolean loaded = false;

    private MockDataLoader() {}

    public static HomeContentResolver.Text getHomeDefaults(Context ctx) {
        ensureLoaded(ctx);
        return cachedHome;
    }

    // The getters return copies sorted by order, so callers can't disturb the shared cache.
    public static List<Chapter> getChapters(Context ctx) {
        ensureLoaded(ctx);
        List<Chapter> copy = new ArrayList<>(cachedChapters);
        Collections.sort(copy, (a, b) -> Long.compare(a.getOrder(), b.getOrder()));
        return copy;
    }

    public static List<Card> getCards(Context ctx, String chapterId) {
        ensureLoaded(ctx);
        List<Card> filtered = new ArrayList<>();
        for (Card c : cachedCards) {
            if (chapterId == null || chapterId.equals(c.getChapterId())) filtered.add(c);
        }
        Collections.sort(filtered, (a, b) -> Long.compare(a.getOrder(), b.getOrder()));
        return filtered;
    }

    public static List<Photo> getGallery(Context ctx) {
        ensureLoaded(ctx);
        List<Photo> copy = new ArrayList<>(cachedPhotos);
        Collections.sort(copy, (a, b) -> Long.compare(a.getOrder(), b.getOrder()));
        return copy;
    }

    public static List<VoiceClip> getVoice(Context ctx) {
        ensureLoaded(ctx);
        return new ArrayList<>(cachedVoice);
    }

    /** The built-in apps list, also shown to signed-in users while the apps collection is empty. */
    public static List<AppEntry> getApps(Context ctx) {
        ensureLoaded(ctx);
        List<AppEntry> copy = new ArrayList<>(cachedApps);
        Collections.sort(copy, (a, b) -> Long.compare(a.getOrder(), b.getOrder()));
        return copy;
    }

    public static Map<String, String> getVoicePlayers(Context ctx) {
        ensureLoaded(ctx);
        return new HashMap<>(cachedVoicePlayers);
    }

    private static synchronized void ensureLoaded(Context ctx) {
        if (loaded) return;

        cachedChapters = new ArrayList<>();
        cachedCards    = new ArrayList<>();
        cachedPhotos   = new ArrayList<>();
        cachedVoice    = new ArrayList<>();
        cachedApps     = new ArrayList<>();
        cachedVoicePlayers = new HashMap<>();

        try {
            String json = readAsset(ctx, ASSET_PATH);
            JSONObject root = new JSONObject(json);

            JSONObject home = root.optJSONObject("home");
            if (home != null) {
                HomeContentResolver.Text sample = HomeContentResolver.Text.fallback();
                cachedHome = new HomeContentResolver.Text(
                        home.optString("title", sample.title),
                        home.optString("subtitle", sample.subtitle),
                        home.optString("countdownLabel", sample.countdownLabel),
                        home.optString("countdownFinish", sample.countdownFinish),
                        home.optString("anniversaryLabel", sample.anniversaryLabel),
                        home.optString("anniversaryUnit", sample.anniversaryUnit));
            }

            JSONArray chArr = root.getJSONArray("chapters");
            for (int i = 0; i < chArr.length(); i++) {
                JSONObject o = chArr.getJSONObject(i);
                cachedChapters.add(new Chapter(
                        o.getString("id"),
                        o.getString("title"),
                        o.optString("description", null),
                        o.optLong("order", 0)
                ));
            }

            JSONArray cdArr = root.getJSONArray("cards");
            for (int i = 0; i < cdArr.length(); i++) {
                JSONObject o = cdArr.getJSONObject(i);
                cachedCards.add(new Card(
                        o.getString("id"),
                        o.getString("title"),
                        o.getString("message"),
                        o.optString("dateLabel", "Sample"),
                        new Timestamp(new Date()),
                        o.optLong("order", 0),
                        o.optString("chapterId", null)
                ));
            }

            JSONArray gArr = root.getJSONArray("gallery");
            for (int i = 0; i < gArr.length(); i++) {
                JSONObject o = gArr.getJSONObject(i);
                cachedPhotos.add(new Photo(
                        o.getString("id"),
                        o.getString("imageUrl"),
                        o.optString("thumbnailUrl", null),
                        o.optString("caption", null),
                        o.optLong("order", 0),
                        Timestamp.now(),
                        null, null
                ));
            }

            JSONObject players = root.optJSONObject("voicePlayers");
            if (players != null) {
                Iterator<String> uids = players.keys();
                while (uids.hasNext()) {
                    String uid = uids.next();
                    cachedVoicePlayers.put(uid, players.optString(uid));
                }
            }

            JSONArray vArr = root.optJSONArray("voice");
            if (vArr != null) {
                for (int i = 0; i < vArr.length(); i++) {
                    JSONObject o = vArr.getJSONObject(i);
                    cachedVoice.add(new VoiceClip(
                            o.getString("id"),
                            null,
                            o.optString("caption", ""),
                            o.optDouble("duration", 0),
                            o.optString("uid", null),
                            null
                    ));
                }
            }

            JSONArray aArr = root.optJSONArray("apps");
            if (aArr != null) {
                for (int i = 0; i < aArr.length(); i++) {
                    JSONObject o = aArr.getJSONObject(i);
                    cachedApps.add(new AppEntry(
                            o.getString("id"),
                            o.optString("name", ""),
                            o.optString("tagline", ""),
                            o.optString("description", ""),
                            o.optString("version", ""),
                            o.optString("icon", ""),
                            o.optString("iconImage", ""),
                            o.optString("downloadUrl", ""),
                            o.optString("sourceUrl", ""),
                            o.optLong("order", i)
                    ));
                }
            }

            loaded = true;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to load mock data", e);
            loaded = true;
        }
    }

    private static String readAsset(Context ctx, String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = ctx.getAssets().open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
}