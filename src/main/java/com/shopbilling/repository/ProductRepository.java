package com.shopbilling.repository;

import com.shopbilling.model.Product;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByQuantityLessThanEqual(BigDecimal quantity);
}
