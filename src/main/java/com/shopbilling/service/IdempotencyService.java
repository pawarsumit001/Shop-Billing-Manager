package com.shopbilling.service;

import com.shopbilling.model.IdempotencyRecord;
import com.shopbilling.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {
    private final IdempotencyRecordRepository records;

    public IdempotencyService(IdempotencyRecordRepository records) {
        this.records = records;
    }

    public void checkAndRemember(String operation, String clientRequestId) {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            return;
        }
        String key = operation + ":" + clientRequestId.trim();
        if (records.existsByRequestKey(key)) {
            throw new IllegalArgumentException("Duplicate request ignored. Please refresh screen.");
        }
        IdempotencyRecord record = new IdempotencyRecord();
        record.setOperation(operation);
        record.setRequestKey(key);
        records.save(record);
    }
}
