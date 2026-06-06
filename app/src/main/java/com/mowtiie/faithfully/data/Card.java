package com.mowtiie.faithfully.data;

import com.google.firebase.Timestamp;

public class Card {

    private String    id;
    private String    title;
    private String    message;
    private String    dateLabel;
    private Timestamp date;
    private long      order;
    private String    chapterId;

    public Card() {}

    public Card(String id, String title, String message, String dateLabel, Timestamp date, long order, String chapterId) {
        this.id        = id;
        this.title     = title;
        this.message   = message;
        this.dateLabel = dateLabel;
        this.date      = date;
        this.order     = order;
        this.chapterId = chapterId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDateLabel() {
        return dateLabel;
    }

    public void setDateLabel(String dateLabel) {
        this.dateLabel = dateLabel;
    }

    public Timestamp getDate() {
        return date;
    }

    public void setDate(Timestamp date) {
        this.date = date;
    }

    public long getOrder() {
        return order;
    }

    public void setOrder(long order) {
        this.order = order;
    }

    public String getChapterId() {
        return chapterId;
    }

    public void setChapterId(String chapterId) {
        this.chapterId = chapterId;
    }
}