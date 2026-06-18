package com.shopbilling.dto;

import com.shopbilling.model.AppUser;
import com.shopbilling.model.Customer;
import com.shopbilling.model.DuePayment;
import com.shopbilling.model.Invoice;
import com.shopbilling.model.InvoiceItem;
import com.shopbilling.model.InvoiceItemLot;
import com.shopbilling.model.PaymentMode;
import com.shopbilling.model.Product;
import com.shopbilling.model.Purchase;
import com.shopbilling.model.ReturnRecord;
import com.shopbilling.model.StockAdjustment;
import com.shopbilling.model.Supplier;
import com.shopbilling.model.SupplierClaim;
import com.shopbilling.model.SupplierPayment;
import com.shopbilling.model.SupplierSettlement;
import com.shopbilling.model.UserRole;
import java.math.BigDecimal;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record BillingRequest(String customerName, String mobileNumber, String address, PaymentMode paymentMode,
                                 boolean gstEnabled, BigDecimal discount, BigDecimal paidAmount,
                                 List<BillingItemRequest> items, String clientRequestId) {
    }

    public record BillingItemRequest(Long productId, BigDecimal quantity) {
    }

    public record DuePaymentRequest(Long customerId, BigDecimal amount, PaymentMode paymentMode, String note,
                                    String clientRequestId) {
    }

    public record StockAdjustmentRequest(Long productId, BigDecimal quantityChange, String reason, String note,
                                         String clientRequestId) {
    }

    public record SupplierClaimRequest(Long productId, Long supplierId, String claimType, String status,
                                       BigDecimal quantity, BigDecimal estimatedAmount, String reason, String note,
                                       String clientRequestId) {
    }

    public record SupplierClaimStatusRequest(String status, String note) {
    }

    public record SupplierPaymentRequest(Long supplierId, BigDecimal amount, PaymentMode paymentMode, String note,
                                         String clientRequestId) {
    }

    public record SupplierSettlementRequest(Long supplierId, Long productId, Long claimId, String settlementType,
                                            BigDecimal quantity, BigDecimal amount, String note, String clientRequestId) {
    }

    public record UserRequest(String name, String username, String password, UserRole role, boolean active) {
    }

    public record AppSettingsDto(String shopName, String shopLogoUrl, String shopAddress, String gstNumber,
                                 String upiId, String invoiceFooter, String defaultGstPercent,
                                 String defaultLowStockAlert, String backupPath) {
    }

    public record InvoiceDto(Long id, String createdAt, String customerName, String mobileNumber, String address,
                             String createdBy, PaymentMode paymentMode, boolean gstEnabled, BigDecimal subtotal,
                             BigDecimal gstTotal, BigDecimal discount, BigDecimal total, BigDecimal paidAmount,
                             BigDecimal dueAmount, List<InvoiceItemDto> items) {
        public static InvoiceDto from(Invoice invoice) {
            return new InvoiceDto(invoice.getId(), String.valueOf(invoice.getCreatedAt()), invoice.getCustomerName(),
                    invoice.getMobileNumber(), invoice.getAddress(), invoice.getCreatedBy(), invoice.getPaymentMode(),
                    invoice.isGstEnabled(), invoice.getSubtotal(), invoice.getGstTotal(), invoice.getDiscount(),
                    invoice.getTotal(), invoice.getPaidAmount(), invoice.getDueAmount(),
                    invoice.getItems().stream().map(InvoiceItemDto::from).toList());
        }
    }

    public record InvoiceItemDto(Long productId, String productName, BigDecimal quantity, BigDecimal rate,
                                 BigDecimal purchasePrice, BigDecimal gstPercent, BigDecimal lineTotal,
                                 List<InvoiceItemLotDto> lots) {
        public static InvoiceItemDto from(InvoiceItem item) {
            return new InvoiceItemDto(item.getProduct() == null ? null : item.getProduct().getId(), item.getProductName(),
                    item.getQuantity(), item.getRate(), item.getPurchasePrice(), item.getGstPercent(), item.getLineTotal(),
                    item.getLots().stream().map(InvoiceItemLotDto::from).toList());
        }
    }

    public record InvoiceItemLotDto(Long purchaseId, Long supplierId, String supplierName, BigDecimal quantity,
                                    BigDecimal purchaseRate, String note) {
        public static InvoiceItemLotDto from(InvoiceItemLot lot) {
            Supplier supplier = lot.getSupplier();
            Purchase purchase = lot.getPurchase();
            return new InvoiceItemLotDto(purchase == null ? null : purchase.getId(),
                    supplier == null ? null : supplier.getId(), supplier == null ? "" : supplier.getName(),
                    lot.getQuantity(), lot.getPurchaseRate(), lot.getNote());
        }
    }

    public record UserDto(Long id, String name, String username, String role, boolean active) {
        public static UserDto from(AppUser user) {
            return new UserDto(user.getId(), user.getName(), user.getUsername(), user.getRole().name(), user.isActive());
        }
    }

    public record PurchaseDto(Long id, Long productId, String productName, Long supplierId, String supplierName,
                              String purchaseDate, BigDecimal quantity, BigDecimal remainingQuantity,
                              BigDecimal rate, BigDecimal total, String note) {
        public static PurchaseDto from(Purchase purchase) {
            Product product = purchase.getProduct();
            Supplier supplier = purchase.getSupplier();
            return new PurchaseDto(purchase.getId(), product == null ? null : product.getId(),
                    product == null ? "" : ApiSupport.productLabel(product), supplier == null ? null : supplier.getId(),
                    supplier == null ? "" : supplier.getName(), String.valueOf(purchase.getPurchaseDate()),
                    purchase.getQuantity(), ApiSupport.nvl(purchase.getRemainingQuantity()),
                    purchase.getRate(), purchase.getTotal(), purchase.getNote());
        }
    }

    public record SupplierDto(Long id, String name, String mobileNumber, String address, String gstNumber,
                              BigDecimal totalPurchases, BigDecimal totalPaid, BigDecimal currentDue,
                              long productCount, String lastPurchaseDate) {
        public static SupplierDto from(Supplier supplier, BigDecimal totalPurchases, BigDecimal totalPaid,
                                       long productCount, String lastPurchaseDate) {
            BigDecimal due = ApiSupport.nvl(totalPurchases).subtract(ApiSupport.nvl(totalPaid));
            if (due.compareTo(BigDecimal.ZERO) < 0) {
                due = BigDecimal.ZERO;
            }
            return new SupplierDto(supplier.getId(), supplier.getName(), supplier.getMobileNumber(),
                    supplier.getAddress(), supplier.getGstNumber(), ApiSupport.nvl(totalPurchases),
                    ApiSupport.nvl(totalPaid), due, productCount, lastPurchaseDate == null ? "" : lastPurchaseDate);
        }
    }

    public record SupplierPaymentDto(Long id, Long supplierId, String supplierName, String paidAt, BigDecimal amount,
                                     BigDecimal beforeDue, BigDecimal afterDue, PaymentMode paymentMode,
                                     String note, String paidBy) {
        public static SupplierPaymentDto from(SupplierPayment payment) {
            Supplier supplier = payment.getSupplier();
            return new SupplierPaymentDto(payment.getId(), supplier == null ? null : supplier.getId(),
                    supplier == null ? "" : supplier.getName(), String.valueOf(payment.getPaidAt()),
                    payment.getAmount(), payment.getBeforeDue(), payment.getAfterDue(),
                    payment.getPaymentMode(), payment.getNote(), payment.getPaidBy());
        }
    }

    public record SupplierSettlementDto(Long id, Long supplierId, String supplierName, Long productId, String productName,
                                        Long claimId, String settledAt, String settlementType, BigDecimal quantity,
                                        BigDecimal amount, String note, String recordedBy) {
        public static SupplierSettlementDto from(SupplierSettlement settlement) {
            Supplier supplier = settlement.getSupplier();
            Product product = settlement.getProduct();
            SupplierClaim claim = settlement.getClaim();
            return new SupplierSettlementDto(settlement.getId(), supplier == null ? null : supplier.getId(),
                    supplier == null ? "" : supplier.getName(), product == null ? null : product.getId(),
                    product == null ? "" : ApiSupport.productLabel(product), claim == null ? null : claim.getId(),
                    String.valueOf(settlement.getSettledAt()), settlement.getSettlementType(),
                    settlement.getQuantity(), settlement.getAmount(), settlement.getNote(), settlement.getRecordedBy());
        }
    }

    public record ReturnDto(Long id, Long invoiceId, Long productId, String productName, String returnedAt,
                            BigDecimal quantity, BigDecimal refundAmount, String reason) {
        public static ReturnDto from(ReturnRecord returnRecord) {
            Product product = returnRecord.getProduct();
            Invoice invoice = returnRecord.getInvoice();
            return new ReturnDto(returnRecord.getId(), invoice == null ? null : invoice.getId(),
                    product == null ? null : product.getId(), product == null ? "" : ApiSupport.productLabel(product),
                    String.valueOf(returnRecord.getReturnedAt()), returnRecord.getQuantity(),
                    returnRecord.getRefundAmount(), returnRecord.getReason());
        }
    }

    public record SoldItemDto(String productName, BigDecimal quantity, BigDecimal amount) {
    }

    public record DuePaymentDto(Long id, Long customerId, String customerName, String mobileNumber, String paidAt,
                                BigDecimal amount, BigDecimal beforeDue, BigDecimal afterDue,
                                PaymentMode paymentMode, String note, String receivedBy) {
        public static DuePaymentDto from(DuePayment payment) {
            Customer customer = payment.getCustomer();
            return new DuePaymentDto(payment.getId(), customer == null ? null : customer.getId(),
                    customer == null ? "" : customer.getName(), customer == null ? "" : customer.getMobileNumber(),
                    String.valueOf(payment.getPaidAt()), payment.getAmount(), payment.getBeforeDue(),
                    payment.getAfterDue(), payment.getPaymentMode(), payment.getNote(), payment.getReceivedBy());
        }
    }

    public record LedgerEntryDto(Long id, String type, String date, String note, BigDecimal amount,
                                 BigDecimal paidAmount, BigDecimal dueAmount, String createdBy) {
    }

    public record CustomerLedgerDto(Customer customer, BigDecimal currentDue, List<LedgerEntryDto> entries) {
    }

    public record SupplierLedgerDto(SupplierDto supplier, BigDecimal totalPurchases, BigDecimal totalPaid,
                                    BigDecimal totalSettled, BigDecimal currentDue, List<PurchaseDto> purchases,
                                    List<SupplierPaymentDto> payments, List<SupplierSettlementDto> settlements,
                                    List<SupplierClaimDto> claims,
                                    List<LedgerEntryDto> entries) {
    }

    public record StockAdjustmentDto(Long id, Long productId, String productName, String adjustedAt,
                                     BigDecimal quantityChange, BigDecimal beforeQuantity, BigDecimal afterQuantity,
                                     String reason, String note, String adjustedBy) {
        public static StockAdjustmentDto from(StockAdjustment adjustment) {
            Product product = adjustment.getProduct();
            return new StockAdjustmentDto(adjustment.getId(), product == null ? null : product.getId(),
                    product == null ? "" : ApiSupport.productLabel(product), String.valueOf(adjustment.getAdjustedAt()),
                    adjustment.getQuantityChange(), adjustment.getBeforeQuantity(), adjustment.getAfterQuantity(),
                    adjustment.getReason(), adjustment.getNote(), adjustment.getAdjustedBy());
        }
    }

    public record SupplierClaimDto(Long id, Long productId, String productName, Long supplierId, String supplierName,
                                   String createdAt, String claimType, String status, BigDecimal quantity,
                                   BigDecimal estimatedAmount, String reason, String note, String createdBy,
                                   String resolvedAt, boolean sentStockDeducted, boolean replacementStockAdded) {
        public static SupplierClaimDto from(SupplierClaim claim) {
            Product product = claim.getProduct();
            Supplier supplier = claim.getSupplier();
            return new SupplierClaimDto(claim.getId(), product == null ? null : product.getId(),
                    product == null ? "" : ApiSupport.productLabel(product),
                    supplier == null ? null : supplier.getId(), supplier == null ? "" : supplier.getName(),
                    String.valueOf(claim.getCreatedAt()), claim.getClaimType(), claim.getStatus(),
                    claim.getQuantity(), claim.getEstimatedAmount(), claim.getReason(), claim.getNote(),
                    claim.getCreatedBy(), String.valueOf(claim.getResolvedAt()),
                    claim.isSentStockDeducted(), claim.isReplacementStockAdded());
        }
    }

    public record ReportDto(BigDecimal dailySales, BigDecimal weeklySales, BigDecimal monthlySales, BigDecimal yearlySales,
                            BigDecimal dailyGst, BigDecimal weeklyGst, BigDecimal monthlyGst, BigDecimal yearlyGst,
                            BigDecimal dailyProfit, BigDecimal weeklyProfit, BigDecimal monthlyProfit, BigDecimal yearlyProfit,
                            BigDecimal customerDues, List<Product> lowStock, List<Customer> customers,
                            List<SoldItemDto> soldToday) {
    }

    public record BootstrapDto(List<Product> products, List<SupplierDto> suppliers, List<Customer> customers,
                               List<InvoiceDto> invoices, List<PurchaseDto> purchases, List<ReturnDto> returns,
                               List<StockAdjustmentDto> stockAdjustments, List<SupplierClaimDto> supplierClaims,
                               List<DuePaymentDto> duePayments, List<SupplierPaymentDto> supplierPayments,
                               List<SupplierSettlementDto> supplierSettlements,
                               ReportDto reports, AppSettingsDto settings) {
    }
}
