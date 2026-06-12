package com.shopbilling.dto;

import com.shopbilling.model.Product;
import java.math.BigDecimal;

public final class ApiSupport {
    private ApiSupport() {
    }

    public static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static String normalizeMobile(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("[^0-9]", "");
    }

    public static boolean isValidIndianMobile(String value) {
        String mobile = normalizeMobile(value);
        return mobile.isBlank() || mobile.matches("[6-9][0-9]{9}");
    }

    public static String productLabel(Product product) {
        if (product.getSize() == null || product.getSize().isBlank()) {
            return product.getName();
        }
        return product.getName() + " (" + product.getSize() + ")";
    }
}
