package com.shopbilling.repository;

import com.shopbilling.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    boolean existsByRequestKey(String requestKey);
}
