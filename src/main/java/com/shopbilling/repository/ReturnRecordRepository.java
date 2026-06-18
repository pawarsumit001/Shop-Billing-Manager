package com.shopbilling.repository;

import com.shopbilling.model.ReturnRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRecordRepository extends JpaRepository<ReturnRecord, Long> {
    List<ReturnRecord> findByInvoiceIdAndProductId(Long invoiceId, Long productId);
}
