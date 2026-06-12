package com.shopbilling.api;

import com.shopbilling.dto.ApiDtos.BillingItemRequest;
import com.shopbilling.dto.ApiDtos.BillingRequest;
import com.shopbilling.dto.ApiDtos.InvoiceDto;
import com.shopbilling.dto.ApiSupport;
import com.shopbilling.model.Invoice;
import com.shopbilling.model.PaymentMode;
import com.shopbilling.repository.InvoiceRepository;
import com.shopbilling.service.BillingService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BillingApiController {
    private static final Logger log = LoggerFactory.getLogger(BillingApiController.class);

    private final InvoiceRepository invoices;
    private final BillingService billingService;

    public BillingApiController(InvoiceRepository invoices, BillingService billingService) {
        this.invoices = invoices;
        this.billingService = billingService;
    }

    @GetMapping("/invoices")
    @Transactional
    public List<InvoiceDto> invoices() {
        return invoices.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(InvoiceDto::from).toList();
    }

    @GetMapping("/invoices/{id}")
    @Transactional
    public ResponseEntity<InvoiceDto> invoice(@PathVariable Long id) {
        return invoices.findById(id).map(invoice -> ResponseEntity.ok(InvoiceDto.from(invoice))).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/billing")
    public ResponseEntity<?> createInvoice(@RequestBody BillingRequest request, Principal principal) {
        log.info("Bill create request by={} customer={} items={}",
                principal == null ? "anonymous" : principal.getName(),
                request == null ? "" : request.customerName(),
                request == null || request.items() == null ? 0 : request.items().size());
        Invoice invoice = new Invoice();
        invoice.setCustomerName(request.customerName());
        if (!ApiSupport.isValidIndianMobile(request.mobileNumber())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Valid 10 digit mobile number enter karo"));
        }
        invoice.setMobileNumber(ApiSupport.normalizeMobile(request.mobileNumber()));
        invoice.setAddress(request.address());
        invoice.setCreatedBy(principal == null ? "system" : principal.getName());
        invoice.setPaymentMode(request.paymentMode() == null ? PaymentMode.CASH : request.paymentMode());
        invoice.setGstEnabled(request.gstEnabled());
        invoice.setDiscount(ApiSupport.nvl(request.discount()));
        invoice.setPaidAmount(ApiSupport.nvl(request.paidAmount()));
        List<BillingItemRequest> items = safeItems(request);
        if (items.isEmpty()) {
            log.warn("Bill create failed by={} reason=no-items", principal == null ? "anonymous" : principal.getName());
            return ResponseEntity.badRequest().body(Map.of("message", "Kam se kam ek product add karo"));
        }
        try {
            Invoice saved = billingService.createInvoice(
                    invoice,
                    items.stream().map(BillingItemRequest::productId).toList(),
                    items.stream().map(BillingItemRequest::quantity).toList());
            log.info("Bill created invoiceId={} by={} total={} due={}",
                    saved.getId(), saved.getCreatedBy(), saved.getTotal(), saved.getDueAmount());
            return ResponseEntity.ok(InvoiceDto.from(saved));
        } catch (IllegalArgumentException ex) {
            log.warn("Bill create failed by={} reason={}", principal == null ? "anonymous" : principal.getName(), ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    private List<BillingItemRequest> safeItems(BillingRequest request) {
        if (request.items() == null) {
            return List.of();
        }
        return request.items().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.productId() != null)
                .filter(item -> item.quantity() != null && item.quantity().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }
}
