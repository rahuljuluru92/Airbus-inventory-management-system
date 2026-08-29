package com.airbus.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Product {

    private Long id;
    private String name;
    private String category;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String supplier;
    private Integer reorderLevel;
    private LocalDateTime lastUpdated;

    public Product() {
    }

    public Product(Long id, String name, String category, Integer quantity, BigDecimal unitPrice,
                   String supplier, Integer reorderLevel, LocalDateTime lastUpdated) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.supplier = supplier;
        this.reorderLevel = reorderLevel;
        this.lastUpdated = lastUpdated;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public Integer getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(Integer reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
