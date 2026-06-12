package com.shopbilling.dto;

import com.shopbilling.model.Product;
import java.math.BigDecimal;

public final class ApiSupport {
    private ApiSupport() {
    }

    public static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static String productLabel(Product product) {
        if (product.getSize() == null || product.getSize().isBlank()) {
            return product.getName();
        }
        return product.getName() + " (" + product.getSize() + ")";
    }
}
