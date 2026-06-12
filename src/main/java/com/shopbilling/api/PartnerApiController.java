package com.shopbilling.api;

import com.shopbilling.dto.ApiDtos.CustomerLedgerDto;
import com.shopbilling.dto.ApiDtos.DuePaymentDto;
import com.shopbilling.dto.ApiDtos.DuePaymentRequest;
import com.shopbilling.dto.ApiDtos.LedgerEntryDto;
import com.shopbilling.dto.ApiDtos.PurchaseDto;
import com.shopbilling.dto.ApiDtos.SupplierDto;
import com.shopbilling.dto.ApiDtos.SupplierLedgerDto;
import com.shopbilling.dto.ApiDtos.SupplierPaymentDto;
import com.shopbilling.dto.ApiDtos.SupplierPaymentRequest;
import com.shopbilling.dto.ApiSupport;
import com.shopbilling.model.Customer;
import com.shopbilling.model.DuePayment;
import com.shopbilling.model.Invoice;
import com.shopbilling.model.PaymentMode;
import com.shopbilling.model.Purchase;
import com.shopbilling.model.Supplier;
import com.shopbilling.model.SupplierPayment;
import com.shopbilling.repository.CustomerRepository;
import com.shopbilling.repository.DuePaymentRepository;
import com.shopbilling.repository.InvoiceRepository;
import com.shopbilling.repository.PurchaseRepository;
import com.shopbilling.repository.ReturnRecordRepository;
import com.shopbilling.repository.SupplierRepository;
import com.shopbilling.repository.SupplierClaimRepository;
import com.shopbilling.repository.SupplierPaymentRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PartnerApiController {
    private final SupplierRepository suppliers;
    private final CustomerRepository customers;
    private final DuePaymentRepository duePayments;
    private final InvoiceRepository invoices;
    private final PurchaseRepository purchases;
    private final ReturnRecordRepository returns;
    private final SupplierClaimRepository supplierClaims;
    private final SupplierPaymentRepository supplierPayments;

    public PartnerApiController(SupplierRepository suppliers, CustomerRepository customers,
                                DuePaymentRepository duePayments, InvoiceRepository invoices,
                                PurchaseRepository purchases, ReturnRecordRepository returns,
                                SupplierClaimRepository supplierClaims, SupplierPaymentRepository supplierPayments) {
        this.suppliers = suppliers;
        this.customers = customers;
        this.duePayments = duePayments;
        this.invoices = invoices;
        this.purchases = purchases;
        this.returns = returns;
        this.supplierClaims = supplierClaims;
        this.supplierPayments = supplierPayments;
    }

    @GetMapping("/suppliers")
    public List<SupplierDto> suppliers() {
        return suppliers.findAll().stream().map(this::supplierSummary).toList();
    }

    @PostMapping("/suppliers")
    public ResponseEntity<?> saveSupplier(@RequestBody Supplier supplier) {
        if (supplier.getName() == null || supplier.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Supplier name required hai"));
        }
        if (!ApiSupport.isValidIndianMobile(supplier.getMobileNumber())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Valid 10 digit mobile number enter karo"));
        }
        supplier.setMobileNumber(ApiSupport.normalizeMobile(supplier.getMobileNumber()));
        return ResponseEntity.ok(supplierSummary(suppliers.save(supplier)));
    }

    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<?> deleteSupplier(@PathVariable Long id) {
        if (!suppliers.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            suppliers.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Supplier deleted"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Supplier purchase/product mein use ho chuka hai, delete nahi kar sakte"));
        }
    }

    @GetMapping("/suppliers/{id}/ledger")
    public ResponseEntity<?> supplierLedger(@PathVariable Long id) {
        Supplier supplier = suppliers.findById(id).orElse(null);
        if (supplier == null) {
            return ResponseEntity.notFound().build();
        }
        List<Purchase> supplierPurchases = purchases.findBySupplierId(id, Sort.by(Sort.Direction.DESC, "purchaseDate"));
        List<SupplierPayment> payments = supplierPayments.findBySupplierId(id, Sort.by(Sort.Direction.DESC, "paidAt"));
        BigDecimal totalPurchases = sumPurchases(supplierPurchases);
        BigDecimal totalPaid = sumPayments(payments);
        BigDecimal currentDue = due(totalPurchases, totalPaid);
        List<LedgerEntryDto> entries = supplierPurchases.stream()
                .map(purchase -> new LedgerEntryDto(purchase.getId(), "PURCHASE", String.valueOf(purchase.getPurchaseDate()),
                        purchase.getNote(), ApiSupport.nvl(purchase.getTotal()), BigDecimal.ZERO, BigDecimal.ZERO, ""))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        payments.forEach(payment -> entries.add(new LedgerEntryDto(payment.getId(), "PAYMENT", String.valueOf(payment.getPaidAt()),
                payment.getNote(), payment.getAmount(), payment.getAmount(), payment.getAfterDue(), payment.getPaidBy())));
        var claims = supplierClaims.findBySupplierId(id, Sort.by(Sort.Direction.DESC, "createdAt"));
        claims.forEach(claim ->
                entries.add(new LedgerEntryDto(claim.getId(), "CLAIM-" + claim.getStatus(), String.valueOf(claim.getCreatedAt()),
                        claim.getProduct() == null ? claim.getReason() : claim.getProduct().getName() + " - " + claim.getReason(),
                        claim.getEstimatedAmount(), BigDecimal.ZERO, BigDecimal.ZERO, claim.getCreatedBy())));
        entries.sort((a, b) -> b.date().compareTo(a.date()));
        return ResponseEntity.ok(new SupplierLedgerDto(supplierSummary(supplier), totalPurchases, totalPaid, currentDue,
                supplierPurchases.stream().map(PurchaseDto::from).toList(),
                payments.stream().map(SupplierPaymentDto::from).toList(),
                claims.stream().map(com.shopbilling.dto.ApiDtos.SupplierClaimDto::from).toList(),
                entries));
    }

    @GetMapping("/supplier-payments")
    public List<SupplierPaymentDto> supplierPayments() {
        return supplierPayments.findAll(Sort.by(Sort.Direction.DESC, "paidAt")).stream().map(SupplierPaymentDto::from).toList();
    }

    @PostMapping("/supplier-payments")
    @Transactional
    public ResponseEntity<?> paySupplier(@RequestBody SupplierPaymentRequest request, Principal principal) {
        if (request.supplierId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Supplier select karna zaroori hai"));
        }
        BigDecimal amount = ApiSupport.nvl(request.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment amount 0 se zyada hona chahiye"));
        }
        Supplier supplier = suppliers.findById(request.supplierId()).orElse(null);
        if (supplier == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Supplier not found"));
        }
        BigDecimal beforeDue = supplierDue(supplier.getId());
        if (beforeDue.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Is supplier ka due already clear hai"));
        }
        if (amount.compareTo(beforeDue) > 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment amount supplier due se zyada hai"));
        }
        SupplierPayment payment = new SupplierPayment();
        payment.setSupplier(supplier);
        payment.setAmount(amount);
        payment.setBeforeDue(beforeDue);
        payment.setAfterDue(beforeDue.subtract(amount));
        payment.setPaymentMode(request.paymentMode() == null ? PaymentMode.CASH : request.paymentMode());
        payment.setNote(request.note());
        payment.setPaidBy(principal == null ? "system" : principal.getName());
        return ResponseEntity.ok(SupplierPaymentDto.from(supplierPayments.save(payment)));
    }

    @GetMapping("/customers")
    public List<Customer> customers() {
        return customers.findAll();
    }

    @PostMapping("/customers")
    public ResponseEntity<?> saveCustomer(@RequestBody Customer customer) {
        boolean hasName = customer.getName() != null && !customer.getName().isBlank();
        boolean hasMobile = customer.getMobileNumber() != null && !customer.getMobileNumber().isBlank();
        if (!hasName && !hasMobile) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer name ya mobile required hai"));
        }
        if (!ApiSupport.isValidIndianMobile(customer.getMobileNumber())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Valid 10 digit mobile number enter karo"));
        }
        customer.setMobileNumber(ApiSupport.normalizeMobile(customer.getMobileNumber()));
        if (customer.getId() == null) {
            Customer existing = hasMobile
                    ? customers.findFirstByMobileNumberOrderByIdAsc(customer.getMobileNumber()).orElse(null)
                    : customers.findFirstByNameIgnoreCaseOrderByIdAsc(customer.getName()).orElse(null);
            if (existing != null) {
                customer.setId(existing.getId());
                if (customer.getDueAmount() == null) {
                    customer.setDueAmount(existing.getDueAmount());
                }
            }
        }
        customer.setDueAmount(ApiSupport.nvl(customer.getDueAmount()));
        return ResponseEntity.ok(customers.save(customer));
    }

    @DeleteMapping("/customers/{id}")
    public ResponseEntity<?> deleteCustomer(@PathVariable Long id) {
        if (!customers.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        Customer customer = customers.findById(id).orElseThrow();
        if (ApiSupport.nvl(customer.getDueAmount()).compareTo(BigDecimal.ZERO) > 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer ka due pending hai, delete nahi kar sakte"));
        }
        customers.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Customer deleted"));
    }

    @GetMapping("/customers/{id}/ledger")
    @Transactional
    public ResponseEntity<?> customerLedger(@PathVariable Long id) {
        Customer customer = customers.findById(id).orElse(null);
        if (customer == null) {
            return ResponseEntity.notFound().build();
        }
        String mobile = customer.getMobileNumber() == null ? "" : customer.getMobileNumber();
        String name = customer.getName() == null ? "" : customer.getName();
        List<LedgerEntryDto> entries = new ArrayList<>();
        invoices.findAll().forEach(invoice -> addInvoiceLedgerEntry(entries, invoice, mobile, name));
        duePayments.findAll().forEach(payment -> {
            if (payment.getCustomer() != null && Objects.equals(payment.getCustomer().getId(), id)) {
                entries.add(new LedgerEntryDto(payment.getId(), "PAYMENT", String.valueOf(payment.getPaidAt()),
                        payment.getNote(), payment.getAmount(), payment.getAmount(), payment.getAfterDue(),
                        payment.getReceivedBy()));
            }
        });
        returns.findAll().forEach(returnRecord -> {
            Invoice invoice = returnRecord.getInvoice();
            if (invoice == null || !matchesCustomer(invoice, mobile, name)) return;
            entries.add(new LedgerEntryDto(returnRecord.getId(), "RETURN", String.valueOf(returnRecord.getReturnedAt()),
                    returnRecord.getReason(), returnRecord.getRefundAmount(), returnRecord.getRefundAmount(),
                    BigDecimal.ZERO, ""));
        });
        entries.sort((a, b) -> b.date().compareTo(a.date()));
        return ResponseEntity.ok(new CustomerLedgerDto(customer, ApiSupport.nvl(customer.getDueAmount()), entries));
    }

    @GetMapping("/due-payments")
    public List<DuePaymentDto> duePayments() {
        return duePayments.findAll(Sort.by(Sort.Direction.DESC, "paidAt")).stream().map(DuePaymentDto::from).toList();
    }

    @PostMapping("/due-payments")
    @Transactional
    public ResponseEntity<?> receiveDuePayment(@RequestBody DuePaymentRequest request, Principal principal) {
        if (request.customerId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer select karna zaroori hai"));
        }
        BigDecimal amount = ApiSupport.nvl(request.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment amount 0 se zyada hona chahiye"));
        }
        Customer customer = customers.findById(request.customerId()).orElse(null);
        if (customer == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Customer not found"));
        }
        BigDecimal beforeDue = ApiSupport.nvl(customer.getDueAmount());
        if (beforeDue.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Is customer ka due already clear hai"));
        }
        if (amount.compareTo(beforeDue) > 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment amount due se zyada hai"));
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
        return ResponseEntity.ok(DuePaymentDto.from(duePayments.save(payment)));
    }

    private void addInvoiceLedgerEntry(List<LedgerEntryDto> entries, Invoice invoice, String mobile, String name) {
        if (!matchesCustomer(invoice, mobile, name)) {
            return;
        }
        entries.add(new LedgerEntryDto(invoice.getId(), "SALE", String.valueOf(invoice.getCreatedAt()),
                "Invoice #" + invoice.getId(), invoice.getTotal(), invoice.getPaidAmount(), invoice.getDueAmount(),
                invoice.getCreatedBy()));
    }

    private boolean matchesCustomer(Invoice invoice, String mobile, String name) {
        boolean matchMobile = !mobile.isBlank() && mobile.equals(invoice.getMobileNumber());
        boolean matchName = !name.isBlank() && name.equalsIgnoreCase(invoice.getCustomerName() == null ? "" : invoice.getCustomerName());
        return matchMobile || matchName;
    }

    private SupplierDto supplierSummary(Supplier supplier) {
        var supplierPurchases = purchases.findBySupplierId(supplier.getId(), Sort.by(Sort.Direction.DESC, "purchaseDate"));
        BigDecimal totalPurchases = sumPurchases(supplierPurchases);
        BigDecimal totalPaid = sumPayments(supplierPayments.findBySupplierId(supplier.getId(), Sort.by(Sort.Direction.DESC, "paidAt")));
        long productCount = supplierPurchases.stream()
                .map(Purchase::getProduct)
                .filter(Objects::nonNull)
                .map(product -> product.getId())
                .filter(Objects::nonNull)
                .distinct()
                .count();
        String lastPurchaseDate = supplierPurchases.isEmpty() ? "" : String.valueOf(supplierPurchases.get(0).getPurchaseDate());
        return SupplierDto.from(supplier, totalPurchases, totalPaid, productCount, lastPurchaseDate);
    }

    private BigDecimal supplierDue(Long supplierId) {
        return due(sumPurchases(purchases.findBySupplierId(supplierId, Sort.unsorted())),
                sumPayments(supplierPayments.findBySupplierId(supplierId, Sort.unsorted())));
    }

    private BigDecimal sumPurchases(List<Purchase> data) {
        return data.stream().map(Purchase::getTotal).map(ApiSupport::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPayments(List<SupplierPayment> data) {
        return data.stream().map(SupplierPayment::getAmount).map(ApiSupport::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal due(BigDecimal totalPurchases, BigDecimal totalPaid) {
        BigDecimal value = ApiSupport.nvl(totalPurchases).subtract(ApiSupport.nvl(totalPaid));
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }
}
