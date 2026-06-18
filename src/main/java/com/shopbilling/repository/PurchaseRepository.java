package com.shopbilling.repository;

import com.shopbilling.model.Purchase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    List<Purchase> findByPurchaseDateBetween(LocalDate start, LocalDate end);
    List<Purchase> findBySupplierId(Long supplierId, Sort sort);
    boolean existsByProductIdAndSupplierId(Long productId, Long supplierId);
    List<Purchase> findByProductIdAndSupplierIdAndRemainingQuantityGreaterThanOrderByPurchaseDateAscIdAsc(Long productId, Long supplierId, BigDecimal remainingQuantity);
    List<Purchase> findByProductIdAndSupplierIdOrderByPurchaseDateDescIdDesc(Long productId, Long supplierId);
    List<Purchase> findByProductIdAndRemainingQuantityGreaterThanOrderByPurchaseDateAscIdAsc(Long productId, BigDecimal remainingQuantity);
}
