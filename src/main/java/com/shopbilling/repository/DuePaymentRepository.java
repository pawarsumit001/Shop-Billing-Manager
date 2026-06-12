package com.shopbilling.repository;

import com.shopbilling.model.DuePayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DuePaymentRepository extends JpaRepository<DuePayment, Long> {
}
