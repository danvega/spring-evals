package com.example.docs;

public class Document {

    private final String id;
    private final String title;
    private String owner;

    public Document(String id, String title, String owner) {
        this.id = id;
        this.title = title;
        this.owner = owner;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
