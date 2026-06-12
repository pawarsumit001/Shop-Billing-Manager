package com.shopbilling.util;

import com.shopbilling.model.Product;

public final class ProductFilters {
    private ProductFilters() {
    }

    public static boolean isValid(Product product) {
        return product != null && product.getName() != null && !product.getName().isBlank();
    }

    public static boolean isLowStock(Product product) {
        return isValid(product)
                && product.getQuantity() != null
                && product.getLowStockAlert() != null
                && product.getQuantity().compareTo(product.getLowStockAlert()) <= 0;
    }
}
