package com.mowtiie.faithfully.helper;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;

/**
 * Tolerant field readers for Firestore documents. DocumentSnapshot's own typed getters
 * (getTimestamp, getString, getLong) throw when a field holds another type, which would crash a
 * whole list for one odd document (for example a date saved as plain milliseconds by an old restore).
 */
public final class DocFields {

    private DocFields() {}

    @Nullable
    public static String string(DocumentSnapshot doc, String field) {
        Object v = doc.get(field);
        return v instanceof String ? (String) v : null;
    }

    public static long number(DocumentSnapshot doc, String field, long fallback) {
        Object v = doc.get(field);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }

    @Nullable
    public static Timestamp timestamp(DocumentSnapshot doc, String field) {
        Object v = doc.get(field);
        if (v instanceof Timestamp) return (Timestamp) v;
        if (v instanceof Date)      return new Timestamp((Date) v);
        if (v instanceof Number)    return new Timestamp(new Date(((Number) v).longValue()));
        return null;
    }
}
