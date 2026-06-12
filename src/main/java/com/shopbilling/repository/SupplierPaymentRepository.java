package com.shopbilling.repository;

import com.shopbilling.model.SupplierPayment;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {
    List<SupplierPayment> findBySupplierId(Long supplierId, Sort sort);
}
