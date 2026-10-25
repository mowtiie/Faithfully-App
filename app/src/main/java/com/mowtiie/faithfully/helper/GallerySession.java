package com.mowtiie.faithfully.helper;

import com.mowtiie.faithfully.data.Photo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The photos the viewer swipes through, handed over from the gallery grid. */
public final class GallerySession {

    private static List<Photo> photos = Collections.emptyList();

    private GallerySession() {}

    public static void set(List<Photo> list) {
        photos = new ArrayList<>(list);
    }

    public static List<Photo> photos() {
        return photos;
    }
}
