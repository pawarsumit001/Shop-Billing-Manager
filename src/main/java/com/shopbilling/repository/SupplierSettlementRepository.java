package com.shopbilling.repository;

import com.shopbilling.model.SupplierSettlement;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierSettlementRepository extends JpaRepository<SupplierSettlement, Long> {
    List<SupplierSettlement> findBySupplierId(Long supplierId, Sort sort);
    List<SupplierSettlement> findByClaimId(Long claimId);
}
