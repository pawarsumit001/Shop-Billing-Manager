package com.shopbilling.service;

import com.shopbilling.dto.ApiDtos.DuePaymentDto;
import com.shopbilling.dto.ApiDtos.DuePaymentRequest;
import com.shopbilling.dto.ApiSupport;
import com.shopbilling.model.Customer;
import com.shopbilling.model.DuePayment;
import com.shopbilling.model.PaymentMode;
import com.shopbilling.repository.CustomerRepository;
import com.shopbilling.repository.DuePaymentRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class DuePaymentService {
    private final CustomerRepository customers;
    private final DuePaymentRepository duePayments;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;

    public DuePaymentService(CustomerRepository customers, DuePaymentRepository duePayments,
                             IdempotencyService idempotencyService, AuditLogService auditLogService) {
        this.customers = customers;
        this.duePayments = duePayments;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
    }

    public List<DuePaymentDto> findAll() {
        return duePayments.findAll(Sort.by(Sort.Direction.DESC, "paidAt")).stream().map(DuePaymentDto::from).toList();
    }

    @Transactional
    public DuePaymentDto receive(DuePaymentRequest request, Principal principal) {
        if (request.customerId() == null) {
            throw new IllegalArgumentException("Customer select karna zaroori hai");
        }
        BigDecimal amount = ApiSupport.nvl(request.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount 0 se zyada hona chahiye");
        }
        idempotencyService.checkAndRemember("due-payment", request.clientRequestId());
        Customer customer = customers.findById(request.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        BigDecimal beforeDue = ApiSupport.nvl(customer.getDueAmount());
        if (beforeDue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Is customer ka due already clear hai");
        }
        if (amount.compareTo(beforeDue) > 0) {
            throw new IllegalArgumentException("Payment amount due se zyada hai");
        }

        BigDecimal afterDue = beforeDue.subtract(amount);
        customer.setDueAmount(afterDue);
        customers.save(customer);

        DuePayment payment = new DuePayment();
        payment.setCustomer(customer);
        payment.setAmount(amount);
        payment.setBeforeDue(beforeDue);
        payment.setAfterDue(afterDue);
        payment.setPaymentMode(request.paymentMode() == null ? PaymentMode.CASH : request.paymentMode());
        payment.setNote(request.note());
        payment.setReceivedBy(principal == null ? "system" : principal.getName());
        DuePayment saved = duePayments.save(payment);
        auditLogService.record(saved.getReceivedBy(), "RECEIVE_DUE_PAYMENT", "Customer", customer.getId(),
                "amount=" + amount + ", before=" + beforeDue + ", after=" + afterDue);
        return DuePaymentDto.from(saved);
    }
}
