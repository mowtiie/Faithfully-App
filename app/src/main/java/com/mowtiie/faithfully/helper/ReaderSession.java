package com.mowtiie.faithfully.helper;

import com.mowtiie.faithfully.data.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The letters the reader is paging through. A chapter can hold a lot of text, so the list is handed
 * over here instead of through Intent extras (which have a size limit). Mirrors the website's
 * js/letter-store.js.
 */
public final class ReaderSession {

    private static List<Card> cards = Collections.emptyList();
    private static String chapterTitle = "";

    private ReaderSession() {}

    public static void set(String title, List<Card> list) {
        chapterTitle = title != null ? title : "";
        cards = new ArrayList<>(list);
    }

    public static List<Card> cards() {
        return cards;
    }

    public static String chapterTitle() {
        return chapterTitle;
    }
}
