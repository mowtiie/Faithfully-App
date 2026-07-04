package com.mowtiie.faithfully.helper;


import android.content.Context;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.mowtiie.faithfully.data.Card;
import com.mowtiie.faithfully.data.Chapter;
import com.mowtiie.faithfully.data.Photo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MockDataLoader {

    private static final String TAG = "MockDataLoader";
    private static final String ASSET_PATH = "mock_data.json";

    private static List<Chapter> cachedChapters;
    private static List<Card> cachedCards;
    private static List<Photo> cachedPhotos;
    private static boolean loaded = false;

    private MockDataLoader() {}

    public static List<Chapter> getChapters(Context ctx) {
        ensureLoaded(ctx);
        return cachedChapters;
    }

    public static List<Card> getCards(Context ctx, String chapterId) {
        ensureLoaded(ctx);
        if (chapterId == null) return cachedCards;
        List<Card> filtered = new ArrayList<>();
        for (Card c : cachedCards) {
            if (chapterId.equals(c.getChapterId())) filtered.add(c);
        }
        return filtered;
    }

    public static List<Photo> getGallery(Context ctx) {
        ensureLoaded(ctx);
        return cachedPhotos;
    }

    private static synchronized void ensureLoaded(Context ctx) {
        if (loaded) return;

        cachedChapters = new ArrayList<>();
        cachedCards    = new ArrayList<>();
        cachedPhotos   = new ArrayList<>();

        try {
            String json = readAsset(ctx, ASSET_PATH);
            JSONObject root = new JSONObject(json);

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