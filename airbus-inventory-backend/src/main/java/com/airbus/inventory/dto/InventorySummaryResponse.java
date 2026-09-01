package com.airbus.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

public class InventorySummaryResponse {

    private final long totalProducts;
    private final BigDecimal totalInventoryValue;
    private final long lowStockCount;
    private final List<CategoryCountResponse> byCategory;

    public InventorySummaryResponse(long totalProducts, BigDecimal totalInventoryValue, long lowStockCount,
                                     List<CategoryCountResponse> byCategory) {
        this.totalProducts = totalProducts;
        this.totalInventoryValue = totalInventoryValue;
        this.lowStockCount = lowStockCount;
        this.byCategory = byCategory;
    }

    public long getTotalProducts() {
        return totalProducts;
    }

    public BigDecimal getTotalInventoryValue() {
        return totalInventoryValue;
    }

    public long getLowStockCount() {
        return lowStockCount;
    }

    public List<CategoryCountResponse> getByCategory() {
        return byCategory;
    }
}
