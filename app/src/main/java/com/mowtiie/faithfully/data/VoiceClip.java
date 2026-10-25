package com.mowtiie.faithfully.data;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;

/**
 * A voice message. Built by hand from the Firestore document (see VoiceFragment), never through
 * toObject, so it needs no ProGuard rule. {@code audioPath} is null for guest sample clips.
 */
public class VoiceClip {

    private final String id;
    @Nullable private final String audioPath;
    private final String caption;
    private final double duration;
    @Nullable private final String uid;
    @Nullable private final Timestamp createdAt;

    public VoiceClip(String id, @Nullable String audioPath, @Nullable String caption, double duration,
                     @Nullable String uid, @Nullable Timestamp createdAt) {
        this.id = id;
        this.audioPath = audioPath;
        this.caption = caption != null ? caption : "";
        this.duration = duration;
        this.uid = uid;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public String getAudioPath() {
        return audioPath;
    }

    public String getCaption() {
        return caption;
    }

    public double getDuration() {
        return duration;
    }

    @Nullable
    public String getUid() {
        return uid;
    }

    @Nullable
    public Timestamp getCreatedAt() {
        return createdAt;
    }
}
