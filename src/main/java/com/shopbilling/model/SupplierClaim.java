package com.shopbilling.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class SupplierClaim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Product product;

    @ManyToOne
    private Supplier supplier;

    private LocalDateTime createdAt = LocalDateTime.now();
    private String claimType = "REPLACEMENT";
    private String status = "PENDING";
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal estimatedAmount = BigDecimal.ZERO;
    private String reason;
    private String note;
    private String createdBy;
    private LocalDateTime resolvedAt;
    private boolean sentStockDeducted;
    private boolean replacementStockAdded;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getClaimType() { return claimType; }
    public void setClaimType(String claimType) { this.claimType = claimType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(BigDecimal estimatedAmount) { this.estimatedAmount = estimatedAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public boolean isSentStockDeducted() { return sentStockDeducted; }
    public void setSentStockDeducted(boolean sentStockDeducted) { this.sentStockDeducted = sentStockDeducted; }
    public boolean isReplacementStockAdded() { return replacementStockAdded; }
    public void setReplacementStockAdded(boolean replacementStockAdded) { this.replacementStockAdded = replacementStockAdded; }
}
