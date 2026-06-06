package com.mowtiie.faithfully.data;

public class Chapter {

    private String id;
    private String title;
    private String description;
    private long   order;

    public Chapter() {}

    public Chapter(String id, String title, String description, long order) {
        this.id          = id;
        this.title       = title;
        this.description = description;
        this.order       = order;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getOrder() {
        return order;
    }

    public void setOrder(long order) {
        this.order = order;
    }
}