package com.shopbilling.service;

import com.shopbilling.dto.ApiDtos.SupplierPaymentDto;
import com.shopbilling.dto.ApiDtos.SupplierPaymentRequest;
import com.shopbilling.dto.ApiDtos.SupplierSettlementDto;
import com.shopbilling.dto.ApiDtos.SupplierSettlementRequest;
import com.shopbilling.dto.ApiSupport;
import com.shopbilling.model.PaymentMode;
import com.shopbilling.model.Supplier;
import com.shopbilling.model.SupplierClaim;
import com.shopbilling.model.SupplierPayment;
import com.shopbilling.model.SupplierSettlement;
import com.shopbilling.repository.ProductRepository;
import com.shopbilling.repository.SupplierClaimRepository;
import com.shopbilling.repository.SupplierPaymentRepository;
import com.shopbilling.repository.SupplierRepository;
import com.shopbilling.repository.SupplierSettlementRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class SupplierFinanceService {
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final SupplierClaimRepository supplierClaims;
    private final SupplierPaymentRepository supplierPayments;
    private final SupplierSettlementRepository supplierSettlements;
    private final SupplierLedgerService supplierLedgerService;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;

    public SupplierFinanceService(SupplierRepository suppliers, ProductRepository products,
                                  SupplierClaimRepository supplierClaims,
                                  SupplierPaymentRepository supplierPayments,
                                  SupplierSettlementRepository supplierSettlements,
                                  SupplierLedgerService supplierLedgerService,
                                  IdempotencyService idempotencyService,
                                  AuditLogService auditLogService) {
        this.suppliers = suppliers;
        this.products = products;
        this.supplierClaims = supplierClaims;
        this.supplierPayments = supplierPayments;
        this.supplierSettlements = supplierSettlements;
        this.supplierLedgerService = supplierLedgerService;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
    }

    public List<SupplierPaymentDto> payments() {
        return supplierPayments.findAll(Sort.by(Sort.Direction.DESC, "paidAt")).stream()
                .map(SupplierPaymentDto::from)
                .toList();
    }

    public List<SupplierSettlementDto> settlements() {
        return supplierSettlements.findAll(Sort.by(Sort.Direction.DESC, "settledAt")).stream()
                .map(SupplierSettlementDto::from)
                .toList();
    }

    @Transactional
    public SupplierSettlementDto settle(SupplierSettlementRequest request, Principal principal) {
        if (request.supplierId() == null) {
            throw new IllegalArgumentException("Supplier select karna zaroori hai");
        }
        BigDecimal amount = ApiSupport.nvl(request.amount());
        BigDecimal quantity = ApiSupport.nvl(request.quantity());
        if (amount.compareTo(BigDecimal.ZERO) < 0 || quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Quantity/amount negative nahi ho sakta");
        }
        if (amount.compareTo(BigDecimal.ZERO) == 0 && quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Settlement quantity ya amount enter karo");
        }
        idempotencyService.checkAndRemember("supplier-settlement", request.clientRequestId());
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        SupplierClaim claim = null;
        if (request.claimId() != null) {
            claim = supplierClaims.findById(request.claimId())
                    .orElseThrow(() -> new IllegalArgumentException("Claim not found"));
            if (claim.getSupplier() == null || !supplier.getId().equals(claim.getSupplier().getId())) {
                throw new IllegalArgumentException("Claim selected supplier se linked nahi hai");
            }
            if ("SETTLED".equalsIgnoreCase(claim.getStatus())) {
                throw new IllegalArgumentException("Claim already settled hai");
            }
            if ("RECEIVED".equalsIgnoreCase(claim.getStatus())) {
                throw new IllegalArgumentException("Replacement received claim ko settlement nahi kar sakte");
            }
            if ("REJECTED".equalsIgnoreCase(claim.getStatus()) || "CLOSED".equalsIgnoreCase(claim.getStatus())) {
                throw new IllegalArgumentException("Closed/rejected claim ko settlement nahi kar sakte");
            }
            BigDecimal previousSettled = supplierSettlements.findByClaimId(claim.getId()).stream()
                    .map(SupplierSettlement::getAmount)
                    .map(ApiSupport::nvl)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal maxAmount = ApiSupport.nvl(claim.getEstimatedAmount());
            if (previousSettled.add(amount).compareTo(maxAmount) > 0) {
                throw new IllegalArgumentException("Settlement amount claim value se zyada hai");
            }
        }

        SupplierSettlement settlement = new SupplierSettlement();
        settlement.setSupplier(supplier);
        settlement.setAmount(amount);
        settlement.setQuantity(quantity);
        settlement.setSettlementType(cleanOrDefault(request.settlementType(), "CREDIT_NOTE"));
        settlement.setNote(request.note());
        settlement.setRecordedBy(principal == null ? "system" : principal.getName());
        if (request.productId() != null) {
            products.findById(request.productId()).ifPresent(settlement::setProduct);
        }
        if (claim != null) {
            settlement.setClaim(claim);
            claim.setStatus("SETTLED");
            claim.setResolvedAt(java.time.LocalDateTime.now());
            supplierClaims.save(claim);
        }
        SupplierSettlement saved = supplierSettlements.save(settlement);
        auditLogService.record(saved.getRecordedBy(), "SUPPLIER_SETTLEMENT", "Supplier", supplier.getId(),
                "amount=" + amount + ", type=" + settlement.getSettlementType());
        return SupplierSettlementDto.from(saved);
    }

    @Transactional
    public SupplierPaymentDto pay(SupplierPaymentRequest request, Principal principal) {
        if (request.supplierId() == null) {
            throw new IllegalArgumentException("Supplier select karna zaroori hai");
        }
        BigDecimal amount = ApiSupport.nvl(request.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount 0 se zyada hona chahiye");
        }
        idempotencyService.checkAndRemember("supplier-payment", request.clientRequestId());
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        BigDecimal beforeDue = supplierLedgerService.supplierDue(supplier.getId());
        if (beforeDue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Is supplier ka due already clear hai");
        }
        if (amount.compareTo(beforeDue) > 0) {
            throw new IllegalArgumentException("Payment amount supplier due se zyada hai");
        }

        SupplierPayment payment = new SupplierPayment();
        payment.setSupplier(supplier);
        payment.setAmount(amount);
        payment.setBeforeDue(beforeDue);
        payment.setAfterDue(beforeDue.subtract(amount));
        payment.setPaymentMode(request.paymentMode() == null ? PaymentMode.CASH : request.paymentMode());
        payment.setNote(request.note());
        payment.setPaidBy(principal == null ? "system" : principal.getName());
        SupplierPayment saved = supplierPayments.save(payment);
        auditLogService.record(saved.getPaidBy(), "SUPPLIER_PAYMENT", "Supplier", supplier.getId(),
                "amount=" + amount + ", before=" + beforeDue + ", after=" + payment.getAfterDue());
        return SupplierPaymentDto.from(saved);
    }

    private String cleanOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }
}
