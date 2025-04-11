package com.postq.model;

/**
 * @author shiguihong
 * @since 2025-04-11
 */
public class Item {

    public enum TYPE{
        DB,TABLE
    }

    private TYPE type;
    private String name;
    private String description;

    public TYPE getType() {
        return type;
    }

    public void setType(TYPE type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return name;
    }
}
