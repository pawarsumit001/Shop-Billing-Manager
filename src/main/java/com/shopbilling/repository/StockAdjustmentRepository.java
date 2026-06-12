package com.shopbilling.repository;

import com.shopbilling.model.StockAdjustment;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {
    List<StockAdjustment> findByProductId(Long productId, Sort sort);
}
