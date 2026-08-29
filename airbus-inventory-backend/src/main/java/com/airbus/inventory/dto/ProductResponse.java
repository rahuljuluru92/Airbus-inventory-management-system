package com.airbus.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductResponse {

    private Long id;
    private String name;
    private String category;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String supplier;
    private Integer reorderLevel;
    private LocalDateTime lastUpdated;

    public ProductResponse(Long id, String name, String category, Integer quantity, BigDecimal unitPrice,
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

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getSupplier() {
        return supplier;
    }

    public Integer getReorderLevel() {
        return reorderLevel;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
}
