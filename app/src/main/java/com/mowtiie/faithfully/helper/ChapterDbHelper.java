package com.mowtiie.faithfully.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.mowtiie.faithfully.data.Chapter;

import java.util.ArrayList;
import java.util.List;

public class ChapterDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "faithfully.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE = "chapters";
    public static final String COL_ID = "firestore_id";
    public static final String COL_TITLE = "title";
    public static final String COL_DESC = "description";
    public static final String COL_ORDER = "sort_order";

    private static ChapterDbHelper instance;

    public static synchronized ChapterDbHelper getInstance(Context ctx) {
        if (instance == null) {
            instance = new ChapterDbHelper(ctx.getApplicationContext());
        }
        return instance;
    }

    private ChapterDbHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE + " (" +
                        COL_ID    + " TEXT PRIMARY KEY, " +
                        COL_TITLE + " TEXT NOT NULL, " +
                        COL_DESC  + " TEXT, " +
                        COL_ORDER + " INTEGER DEFAULT 0" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public void upsert(Chapter chapter) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ID,    chapter.getId());
        cv.put(COL_TITLE, chapter.getTitle());
        cv.put(COL_DESC,  chapter.getDescription());
        cv.put(COL_ORDER, chapter.getOrder());
        db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void upsertAll(List<Chapter> chapters) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Chapter c : chapters) {
                ContentValues cv = new ContentValues();
                cv.put(COL_ID,    c.getId());
                cv.put(COL_TITLE, c.getTitle());
                cv.put(COL_DESC,  c.getDescription());
                cv.put(COL_ORDER, c.getOrder());
                db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void delete(String firestoreId) {
        getWritableDatabase().delete(TABLE, COL_ID + "=?", new String[]{ firestoreId });
    }

    public List<Chapter> getAll() {
        List<Chapter> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(TABLE, null, null, null, null, null, COL_ORDER + " ASC");

        if (cursor.moveToFirst()) {
            do {
                list.add(fromCursor(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public Chapter getById(String firestoreId) {
        Cursor cursor = getReadableDatabase().query(
                TABLE, null,
                COL_ID + "=?", new String[]{ firestoreId },
                null, null, null);

        Chapter chapter = null;
        if (cursor.moveToFirst()) {
            chapter = fromCursor(cursor);
        }
        cursor.close();
        return chapter;
    }

    public void clearAll() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    private Chapter fromCursor(Cursor cursor) {
        return new Chapter(
                cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_DESC)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_ORDER))
        );
    }
}
