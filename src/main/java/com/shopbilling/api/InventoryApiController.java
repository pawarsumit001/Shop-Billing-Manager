package com.shopbilling.api;

import com.shopbilling.dto.ApiDtos.PurchaseDto;
import com.shopbilling.dto.ApiDtos.ReturnDto;
import com.shopbilling.dto.ApiDtos.StockAdjustmentDto;
import com.shopbilling.dto.ApiDtos.StockAdjustmentRequest;
import com.shopbilling.dto.ApiDtos.SupplierClaimDto;
import com.shopbilling.dto.ApiDtos.SupplierClaimRequest;
import com.shopbilling.dto.ApiDtos.SupplierClaimStatusRequest;
import com.shopbilling.dto.ApiSupport;
import com.shopbilling.model.Product;
import com.shopbilling.model.Purchase;
import com.shopbilling.model.ReturnRecord;
import com.shopbilling.model.StockAdjustment;
import com.shopbilling.model.SupplierClaim;
import com.shopbilling.repository.ProductRepository;
import com.shopbilling.repository.PurchaseRepository;
import com.shopbilling.repository.ReturnRecordRepository;
import com.shopbilling.repository.StockAdjustmentRepository;
import com.shopbilling.repository.SupplierClaimRepository;
import com.shopbilling.repository.SupplierRepository;
import com.shopbilling.service.AuditLogService;
import com.shopbilling.service.IdempotencyService;
import com.shopbilling.service.StockService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
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
public class InventoryApiController {
    private static final Logger log = LoggerFactory.getLogger(InventoryApiController.class);

    private final ProductRepository products;
    private final PurchaseRepository purchases;
    private final ReturnRecordRepository returns;
    private final StockAdjustmentRepository stockAdjustments;
    private final StockService stockService;
    private final SupplierClaimRepository supplierClaims;
    private final SupplierRepository suppliers;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;

    public InventoryApiController(ProductRepository products, PurchaseRepository purchases,
                                  ReturnRecordRepository returns, StockAdjustmentRepository stockAdjustments,
                                  StockService stockService, SupplierClaimRepository supplierClaims,
                                  SupplierRepository suppliers, IdempotencyService idempotencyService,
                                  AuditLogService auditLogService) {
        this.products = products;
        this.purchases = purchases;
        this.returns = returns;
        this.stockAdjustments = stockAdjustments;
        this.stockService = stockService;
        this.supplierClaims = supplierClaims;
        this.suppliers = suppliers;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/purchases")
    public List<PurchaseDto> purchases() {
        return purchases.findAll(Sort.by(Sort.Direction.DESC, "purchaseDate")).stream().map(PurchaseDto::from).toList();
    }

    @PostMapping("/purchases")
    public ResponseEntity<?> savePurchase(@RequestBody Purchase purchase, Principal principal) {
        try {
            idempotencyService.checkAndRemember("purchase", purchase.getClientRequestId());
            Purchase saved = stockService.recordPurchase(purchase);
            auditLogService.record(actor(principal), "PURCHASE_STOCK", "Purchase", saved.getId(),
                    "product=" + (saved.getProduct() == null ? "" : saved.getProduct().getId()) + ", qty=" + saved.getQuantity());
            log.info("Purchase saved id={} product={} quantity={} rate={} total={}",
                    saved.getId(),
                    saved.getProduct() == null ? null : saved.getProduct().getId(),
                    saved.getQuantity(), saved.getRate(), saved.getTotal());
            return ResponseEntity.ok(PurchaseDto.from(saved));
        } catch (IllegalArgumentException ex) {
            log.warn("Purchase save failed reason={}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/returns")
    public List<ReturnDto> returns() {
        return returns.findAll(Sort.by(Sort.Direction.DESC, "returnedAt")).stream().map(ReturnDto::from).toList();
    }

    @PostMapping("/returns")
    public ResponseEntity<?> saveReturn(@RequestBody ReturnRecord returnRecord, Principal principal) {
        try {
            idempotencyService.checkAndRemember("return", returnRecord.getClientRequestId());
            ReturnRecord saved = stockService.recordReturn(returnRecord);
            auditLogService.record(actor(principal), "RETURN_ITEM", "ReturnRecord", saved.getId(),
                    "product=" + (saved.getProduct() == null ? "" : saved.getProduct().getId()) + ", qty=" + saved.getQuantity());
            return ResponseEntity.ok(ReturnDto.from(saved));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/stock-adjustments")
    public List<StockAdjustmentDto> stockAdjustments() {
        return stockAdjustments.findAll(Sort.by(Sort.Direction.DESC, "adjustedAt")).stream().map(StockAdjustmentDto::from).toList();
    }

    @PostMapping("/stock-adjustments")
    @Transactional
    public ResponseEntity<?> saveStockAdjustment(@RequestBody StockAdjustmentRequest request, Principal principal) {
        try {
            idempotencyService.checkAndRemember("stock-adjustment", request.clientRequestId());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
        if (request.productId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Product select karna zaroori hai"));
        }
        BigDecimal quantityChange = ApiSupport.nvl(request.quantityChange());
        if (quantityChange.compareTo(BigDecimal.ZERO) == 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Quantity change 0 nahi ho sakta"));
        }
        if (!isValidStockAdjustmentReason(request.reason())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Valid stock adjustment reason select karo"));
        }
        if (("OPENING_CORRECTION".equalsIgnoreCase(request.reason()) || "CLAIM_CORRECTION".equalsIgnoreCase(request.reason()))
                && (request.note() == null || request.note().isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Correction ke liye note mandatory hai"));
        }
        Product product = products.findById(request.productId()).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Product not found"));
        }
        BigDecimal before = ApiSupport.nvl(product.getQuantity());
        BigDecimal after = before.add(quantityChange);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Stock negative nahi ho sakta"));
        }
        product.setQuantity(after);
        products.save(product);
        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setProduct(product);
        adjustment.setQuantityChange(quantityChange);
        adjustment.setBeforeQuantity(before);
        adjustment.setAfterQuantity(after);
        adjustment.setReason(request.reason());
        adjustment.setNote(request.note());
        adjustment.setAdjustedBy(principal == null ? "system" : principal.getName());
        log.info("Stock adjusted product={} before={} change={} after={} by={}",
                product.getId(), before, quantityChange, after, adjustment.getAdjustedBy());
        StockAdjustment saved = stockAdjustments.save(adjustment);
        auditLogService.record(saved.getAdjustedBy(), "STOCK_ADJUSTMENT", "Product", product.getId(),
                "before=" + before + ", change=" + quantityChange + ", after=" + after + ", reason=" + request.reason());
        return ResponseEntity.ok(StockAdjustmentDto.from(saved));
    }

    @GetMapping("/supplier-claims")
    public List<SupplierClaimDto> supplierClaims() {
        return supplierClaims.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(SupplierClaimDto::from).toList();
    }

    @PostMapping("/supplier-claims")
    @Transactional
    public ResponseEntity<?> saveSupplierClaim(@RequestBody SupplierClaimRequest request, Principal principal) {
        try {
            idempotencyService.checkAndRemember("supplier-claim", request.clientRequestId());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
        if (request.productId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Product select karna zaroori hai"));
        }
        if (request.supplierId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Supplier/shop select karna zaroori hai"));
        }
        BigDecimal quantity = ApiSupport.nvl(request.quantity());
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Quantity 0 se zyada honi chahiye"));
        }
        if (request.reason() == null || request.reason().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Claim reason required hai"));
        }
        if (ApiSupport.nvl(request.estimatedAmount()).compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Estimated amount negative nahi ho sakta"));
        }
        Product product = products.findById(request.productId()).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Product not found"));
        }
        if (ApiSupport.nvl(product.getQuantity()).compareTo(quantity) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Claim quantity available stock se zyada hai"));
        }
        var supplier = suppliers.findById(request.supplierId()).orElse(null);
        if (supplier == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Supplier not found"));
        }
        if (!purchases.existsByProductIdAndSupplierId(product.getId(), supplier.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Selected supplier se is product ka purchase record nahi hai"));
        }
        if (supplierAvailableQuantity(product.getId(), supplier.getId()).compareTo(quantity) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Selected supplier ke available lot mein enough quantity nahi hai"));
        }
        SupplierClaim claim = new SupplierClaim();
        claim.setProduct(product);
        claim.setSupplier(supplier);
        claim.setClaimType(cleanOrDefault(request.claimType(), "REPLACEMENT"));
        claim.setStatus("PENDING");
        claim.setQuantity(quantity);
        claim.setEstimatedAmount(ApiSupport.nvl(request.estimatedAmount()));
        claim.setReason(request.reason());
        claim.setNote(request.note());
        claim.setCreatedBy(principal == null ? "system" : principal.getName());
        product.setQuantity(ApiSupport.nvl(product.getQuantity()).subtract(quantity));
        deductSupplierLots(product.getId(), supplier.getId(), quantity);
        products.save(product);
        claim.setSentStockDeducted(true);
        SupplierClaim saved = supplierClaims.save(claim);
        auditLogService.record(saved.getCreatedBy(), "CREATE_SUPPLIER_CLAIM", "SupplierClaim", saved.getId(),
                "product=" + product.getId() + ", supplier=" + supplier.getId() + ", qty=" + quantity);
        return ResponseEntity.ok(SupplierClaimDto.from(saved));
    }

    @PostMapping("/supplier-claims/{id}/status")
    @Transactional
    public ResponseEntity<?> updateSupplierClaimStatus(@PathVariable Long id, @RequestBody SupplierClaimStatusRequest request,
                                                       Principal principal) {
        SupplierClaim claim = supplierClaims.findById(id).orElse(null);
        if (claim == null) {
            return ResponseEntity.notFound().build();
        }
        String nextStatus = cleanOrDefault(request.status(), claim.getStatus());
        if (!isValidClaimTransition(claim.getStatus(), nextStatus)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid claim status transition: " + claim.getStatus() + " to " + nextStatus));
        }
        claim.setStatus(nextStatus);
        if (request.note() != null && !request.note().isBlank()) {
            claim.setNote(request.note());
        }
        if ("RECEIVED".equalsIgnoreCase(claim.getStatus()) && !claim.isReplacementStockAdded() && claim.getProduct() != null) {
            Product product = claim.getProduct();
            product.setQuantity(ApiSupport.nvl(product.getQuantity()).add(ApiSupport.nvl(claim.getQuantity())));
            restoreSupplierLotForReceivedClaim(claim);
            products.save(product);
            claim.setReplacementStockAdded(true);
        }
        if ("RECEIVED".equalsIgnoreCase(claim.getStatus()) || "REJECTED".equalsIgnoreCase(claim.getStatus()) || "SETTLED".equalsIgnoreCase(claim.getStatus())) {
            claim.setResolvedAt(java.time.LocalDateTime.now());
        }
        SupplierClaim saved = supplierClaims.save(claim);
        auditLogService.record(actor(principal), "UPDATE_SUPPLIER_CLAIM_STATUS", "SupplierClaim", saved.getId(),
                "status=" + saved.getStatus());
        return ResponseEntity.ok(SupplierClaimDto.from(saved));
    }

    private String cleanOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }

    private BigDecimal supplierAvailableQuantity(Long productId, Long supplierId) {
        return purchases.findByProductIdAndSupplierIdAndRemainingQuantityGreaterThanOrderByPurchaseDateAscIdAsc(productId, supplierId, BigDecimal.ZERO)
                .stream()
                .map(Purchase::getRemainingQuantity)
                .map(ApiSupport::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void deductSupplierLots(Long productId, Long supplierId, BigDecimal quantity) {
        BigDecimal remaining = quantity;
        for (Purchase lot : purchases.findByProductIdAndSupplierIdAndRemainingQuantityGreaterThanOrderByPurchaseDateAscIdAsc(productId, supplierId, BigDecimal.ZERO)) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = ApiSupport.nvl(lot.getRemainingQuantity());
            BigDecimal consume = available.min(remaining);
            lot.setRemainingQuantity(available.subtract(consume));
            purchases.save(lot);
            remaining = remaining.subtract(consume);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Selected supplier ke lot se claim quantity deduct nahi ho paayi");
        }
    }

    private void restoreSupplierLotForReceivedClaim(SupplierClaim claim) {
        if (claim.getProduct() == null || claim.getSupplier() == null) {
            return;
        }
        List<Purchase> lots = purchases.findByProductIdAndSupplierIdOrderByPurchaseDateDescIdDesc(
                claim.getProduct().getId(), claim.getSupplier().getId());
        if (lots.isEmpty()) {
            return;
        }
        Purchase lot = lots.get(0);
        lot.setRemainingQuantity(ApiSupport.nvl(lot.getRemainingQuantity()).add(ApiSupport.nvl(claim.getQuantity())));
        purchases.save(lot);
    }

    private boolean isValidClaimTransition(String current, String next) {
        String from = cleanOrDefault(current, "PENDING");
        String to = cleanOrDefault(next, from);
        if (from.equals(to)) {
            return true;
        }
        if ("PENDING".equals(from)) {
            return "SENT".equals(to) || "REJECTED".equals(to) || "SETTLED".equals(to);
        }
        if ("SENT".equals(from)) {
            return "RECEIVED".equals(to) || "REJECTED".equals(to) || "SETTLED".equals(to);
        }
        return false;
    }

    private boolean isValidStockAdjustmentReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        String value = reason.trim().toUpperCase();
        return "DAMAGED".equals(value) || "LOST".equals(value) || "FOUND".equals(value)
                || "OPENING_CORRECTION".equals(value) || "CLAIM_CORRECTION".equals(value);
    }

    private String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
