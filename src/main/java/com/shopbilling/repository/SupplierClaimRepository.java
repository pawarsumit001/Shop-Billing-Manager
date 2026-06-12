package com.shopbilling.repository;

import com.shopbilling.model.SupplierClaim;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierClaimRepository extends JpaRepository<SupplierClaim, Long> {
    List<SupplierClaim> findBySupplierId(Long supplierId, Sort sort);
}
