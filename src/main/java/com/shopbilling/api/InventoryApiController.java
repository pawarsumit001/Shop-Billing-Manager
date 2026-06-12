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

    public InventoryApiController(ProductRepository products, PurchaseRepository purchases,
                                  ReturnRecordRepository returns, StockAdjustmentRepository stockAdjustments,
                                  StockService stockService, SupplierClaimRepository supplierClaims,
                                  SupplierRepository suppliers) {
        this.products = products;
        this.purchases = purchases;
        this.returns = returns;
        this.stockAdjustments = stockAdjustments;
        this.stockService = stockService;
        this.supplierClaims = supplierClaims;
        this.suppliers = suppliers;
    }

    @GetMapping("/purchases")
    public List<PurchaseDto> purchases() {
        return purchases.findAll(Sort.by(Sort.Direction.DESC, "purchaseDate")).stream().map(PurchaseDto::from).toList();
    }

    @PostMapping("/purchases")
    public ResponseEntity<?> savePurchase(@RequestBody Purchase purchase) {
        try {
            Purchase saved = stockService.recordPurchase(purchase);
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
    public ResponseEntity<?> saveReturn(@RequestBody ReturnRecord returnRecord) {
        try {
            return ResponseEntity.ok(ReturnDto.from(stockService.recordReturn(returnRecord)));
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
        if (request.productId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Product select karna zaroori hai"));
        }
        BigDecimal quantityChange = ApiSupport.nvl(request.quantityChange());
        if (quantityChange.compareTo(BigDecimal.ZERO) == 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Quantity change 0 nahi ho sakta"));
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
        return ResponseEntity.ok(StockAdjustmentDto.from(stockAdjustments.save(adjustment)));
    }

    @GetMapping("/supplier-claims")
    public List<SupplierClaimDto> supplierClaims() {
        return supplierClaims.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(SupplierClaimDto::from).toList();
    }

    @PostMapping("/supplier-claims")
    @Transactional
    public ResponseEntity<?> saveSupplierClaim(@RequestBody SupplierClaimRequest request, Principal principal) {
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
        SupplierClaim claim = new SupplierClaim();
        claim.setProduct(product);
        claim.setSupplier(supplier);
        claim.setClaimType(cleanOrDefault(request.claimType(), "REPLACEMENT"));
        claim.setStatus(cleanOrDefault(request.status(), "PENDING"));
        claim.setQuantity(quantity);
        claim.setEstimatedAmount(ApiSupport.nvl(request.estimatedAmount()));
        claim.setReason(request.reason());
        claim.setNote(request.note());
        claim.setCreatedBy(principal == null ? "system" : principal.getName());
        product.setQuantity(ApiSupport.nvl(product.getQuantity()).subtract(quantity));
        products.save(product);
        claim.setSentStockDeducted(true);
        return ResponseEntity.ok(SupplierClaimDto.from(supplierClaims.save(claim)));
    }

    @PostMapping("/supplier-claims/{id}/status")
    @Transactional
    public ResponseEntity<?> updateSupplierClaimStatus(@PathVariable Long id, @RequestBody SupplierClaimStatusRequest request) {
        SupplierClaim claim = supplierClaims.findById(id).orElse(null);
        if (claim == null) {
            return ResponseEntity.notFound().build();
        }
        claim.setStatus(cleanOrDefault(request.status(), claim.getStatus()));
        if (request.note() != null && !request.note().isBlank()) {
            claim.setNote(request.note());
        }
        if ("RECEIVED".equalsIgnoreCase(claim.getStatus()) && !claim.isReplacementStockAdded() && claim.getProduct() != null) {
            Product product = claim.getProduct();
            product.setQuantity(ApiSupport.nvl(product.getQuantity()).add(ApiSupport.nvl(claim.getQuantity())));
            products.save(product);
            claim.setReplacementStockAdded(true);
        }
        if ("RECEIVED".equalsIgnoreCase(claim.getStatus()) || "REJECTED".equalsIgnoreCase(claim.getStatus()) || "SETTLED".equalsIgnoreCase(claim.getStatus())) {
            claim.setResolvedAt(java.time.LocalDateTime.now());
        }
        return ResponseEntity.ok(SupplierClaimDto.from(supplierClaims.save(claim)));
    }

    private String cleanOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }
}
