package com.shopbilling.service;

import com.shopbilling.dto.ApiDtos.LedgerEntryDto;
import com.shopbilling.dto.ApiDtos.PurchaseDto;
import com.shopbilling.dto.ApiDtos.SupplierDto;
import com.shopbilling.dto.ApiDtos.SupplierLedgerDto;
import com.shopbilling.dto.ApiDtos.SupplierPaymentDto;
import com.shopbilling.dto.ApiDtos.SupplierSettlementDto;
import com.shopbilling.dto.ApiSupport;
import com.shopbilling.model.Purchase;
import com.shopbilling.model.Supplier;
import com.shopbilling.model.SupplierPayment;
import com.shopbilling.model.SupplierSettlement;
import com.shopbilling.repository.PurchaseRepository;
import com.shopbilling.repository.SupplierClaimRepository;
import com.shopbilling.repository.SupplierPaymentRepository;
import com.shopbilling.repository.SupplierRepository;
import com.shopbilling.repository.SupplierSettlementRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class SupplierLedgerService {
    private final SupplierRepository suppliers;
    private final PurchaseRepository purchases;
    private final SupplierPaymentRepository supplierPayments;
    private final SupplierSettlementRepository supplierSettlements;
    private final SupplierClaimRepository supplierClaims;

    public SupplierLedgerService(SupplierRepository suppliers, PurchaseRepository purchases,
                                 SupplierPaymentRepository supplierPayments,
                                 SupplierSettlementRepository supplierSettlements,
                                 SupplierClaimRepository supplierClaims) {
        this.suppliers = suppliers;
        this.purchases = purchases;
        this.supplierPayments = supplierPayments;
        this.supplierSettlements = supplierSettlements;
        this.supplierClaims = supplierClaims;
    }

    public SupplierLedgerDto ledger(Long supplierId) {
        Supplier supplier = suppliers.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        List<Purchase> supplierPurchases = purchases.findBySupplierId(supplierId, Sort.by(Sort.Direction.DESC, "purchaseDate"));
        List<SupplierPayment> payments = supplierPayments.findBySupplierId(supplierId, Sort.by(Sort.Direction.DESC, "paidAt"));
        List<SupplierSettlement> settlements = supplierSettlements.findBySupplierId(supplierId, Sort.by(Sort.Direction.DESC, "settledAt"));
        var claims = supplierClaims.findBySupplierId(supplierId, Sort.by(Sort.Direction.DESC, "createdAt"));

        BigDecimal totalPurchases = sumPurchases(supplierPurchases);
        BigDecimal totalPaid = sumPayments(payments);
        BigDecimal totalSettled = sumSettlements(settlements);
        List<LedgerEntryDto> entries = new ArrayList<>();

        supplierPurchases.forEach(purchase -> entries.add(new LedgerEntryDto(purchase.getId(), "PURCHASE",
                String.valueOf(purchase.getPurchaseDate()), purchase.getNote(), ApiSupport.nvl(purchase.getTotal()),
                BigDecimal.ZERO, BigDecimal.ZERO, "")));
        payments.forEach(payment -> entries.add(new LedgerEntryDto(payment.getId(), "PAYMENT",
                String.valueOf(payment.getPaidAt()), payment.getNote(), payment.getAmount(), payment.getAmount(),
                payment.getAfterDue(), payment.getPaidBy())));
        settlements.forEach(settlement -> entries.add(new LedgerEntryDto(settlement.getId(),
                "SETTLEMENT-" + settlement.getSettlementType(), String.valueOf(settlement.getSettledAt()),
                settlement.getNote(), settlement.getAmount(), settlement.getAmount(), BigDecimal.ZERO,
                settlement.getRecordedBy())));
        claims.forEach(claim -> entries.add(new LedgerEntryDto(claim.getId(), "CLAIM-" + claim.getStatus(),
                String.valueOf(claim.getCreatedAt()),
                claim.getProduct() == null ? claim.getReason() : claim.getProduct().getName() + " - " + claim.getReason(),
                claim.getEstimatedAmount(), BigDecimal.ZERO, BigDecimal.ZERO, claim.getCreatedBy())));
        entries.sort((a, b) -> b.date().compareTo(a.date()));

        return new SupplierLedgerDto(summary(supplier), totalPurchases, totalPaid, totalSettled,
                due(totalPurchases, totalPaid, totalSettled),
                supplierPurchases.stream().map(PurchaseDto::from).toList(),
                payments.stream().map(SupplierPaymentDto::from).toList(),
                settlements.stream().map(SupplierSettlementDto::from).toList(),
                claims.stream().map(com.shopbilling.dto.ApiDtos.SupplierClaimDto::from).toList(),
                entries);
    }

    public SupplierDto summary(Supplier supplier) {
        var supplierPurchases = purchases.findBySupplierId(supplier.getId(), Sort.by(Sort.Direction.DESC, "purchaseDate"));
        BigDecimal totalPurchases = sumPurchases(supplierPurchases);
        BigDecimal totalPaid = sumPayments(supplierPayments.findBySupplierId(supplier.getId(), Sort.unsorted()));
        BigDecimal totalSettled = sumSettlements(supplierSettlements.findBySupplierId(supplier.getId(), Sort.unsorted()));
        long productCount = supplierPurchases.stream()
                .map(Purchase::getProduct)
                .filter(Objects::nonNull)
                .map(product -> product.getId())
                .filter(Objects::nonNull)
                .distinct()
                .count();
        String lastPurchaseDate = supplierPurchases.isEmpty() ? "" : String.valueOf(supplierPurchases.get(0).getPurchaseDate());
        return SupplierDto.from(supplier, totalPurchases.subtract(totalSettled), totalPaid, productCount, lastPurchaseDate);
    }

    public BigDecimal supplierDue(Long supplierId) {
        return due(sumPurchases(purchases.findBySupplierId(supplierId, Sort.unsorted())),
                sumPayments(supplierPayments.findBySupplierId(supplierId, Sort.unsorted())),
                sumSettlements(supplierSettlements.findBySupplierId(supplierId, Sort.unsorted())));
    }

    private BigDecimal sumPurchases(List<Purchase> data) {
        return data.stream().map(Purchase::getTotal).map(ApiSupport::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPayments(List<SupplierPayment> data) {
        return data.stream().map(SupplierPayment::getAmount).map(ApiSupport::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumSettlements(List<SupplierSettlement> data) {
        return data.stream().map(SupplierSettlement::getAmount).map(ApiSupport::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal due(BigDecimal totalPurchases, BigDecimal totalPaid, BigDecimal totalSettled) {
        BigDecimal value = ApiSupport.nvl(totalPurchases)
                .subtract(ApiSupport.nvl(totalPaid))
                .subtract(ApiSupport.nvl(totalSettled));
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }
}
