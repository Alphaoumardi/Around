package com.around.appl.models;

public class ModelItem {
    private String name;
    private String fileUrl;

    public ModelItem() {}

    public ModelItem(String name, String fileUrl) {
        this.name = name;
        this.fileUrl = fileUrl;
    }

    public String getName() {
        return name;
    }

    public String getFileUrl() {
        return fileUrl;
    }
}

