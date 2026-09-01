package com.airbus.inventory.dto;

public class CategoryCountResponse {

    private final String category;
    private final long count;

    public CategoryCountResponse(String category, long count) {
        this.category = category;
        this.count = count;
    }

    public String getCategory() {
        return category;
    }

    public long getCount() {
        return count;
    }
}
