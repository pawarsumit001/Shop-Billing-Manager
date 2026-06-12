package com.shopbilling.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

@Entity
public class InvoiceItemLot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private InvoiceItem invoiceItem;

    @ManyToOne
    private Purchase purchase;

    @ManyToOne
    private Supplier supplier;

    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal purchaseRate = BigDecimal.ZERO;
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public InvoiceItem getInvoiceItem() { return invoiceItem; }
    public void setInvoiceItem(InvoiceItem invoiceItem) { this.invoiceItem = invoiceItem; }
    public Purchase getPurchase() { return purchase; }
    public void setPurchase(Purchase purchase) { this.purchase = purchase; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getPurchaseRate() { return purchaseRate; }
    public void setPurchaseRate(BigDecimal purchaseRate) { this.purchaseRate = purchaseRate; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
