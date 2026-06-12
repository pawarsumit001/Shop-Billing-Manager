package com.shopbilling.repository;

import com.shopbilling.model.Invoice;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
