package com.shopbilling.service;

import com.shopbilling.model.Customer;
import com.shopbilling.model.Invoice;
import com.shopbilling.model.InvoiceItem;
import com.shopbilling.model.InvoiceItemLot;
import com.shopbilling.model.PaymentMode;
import com.shopbilling.model.Product;
import com.shopbilling.model.Purchase;
import com.shopbilling.repository.CustomerRepository;
import com.shopbilling.repository.InvoiceRepository;
import com.shopbilling.repository.ProductRepository;
import com.shopbilling.repository.PurchaseRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BillingService {
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final PurchaseRepository purchases;

    public BillingService(ProductRepository products, CustomerRepository customers, InvoiceRepository invoices,
                          PurchaseRepository purchases) {
        this.products = products;
        this.customers = customers;
        this.invoices = invoices;
        this.purchases = purchases;
    }

    @Transactional
    public Invoice createInvoice(Invoice invoice, List<Long> productIds, List<BigDecimal> quantities) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;

        for (int i = 0; i < productIds.size(); i++) {
            BigDecimal quantity = quantities.get(i);
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Product product = products.findById(productIds.get(i))
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            if (product.getQuantity().compareTo(quantity) < 0) {
                throw new IllegalArgumentException(product.getName() + " ka stock kam hai");
            }

            BigDecimal base = product.getSellingPrice().multiply(quantity);
            BigDecimal gstPercent = invoice.isGstEnabled() ? product.getGstPercent() : BigDecimal.ZERO;
            BigDecimal gst = base.multiply(gstPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = base.add(gst);

            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setProduct(product);
            item.setProductName(productLabel(product));
            item.setQuantity(quantity);
            item.setRate(product.getSellingPrice());
            item.setPurchasePrice(nvl(product.getPurchasePrice()));
            item.setGstPercent(gstPercent);
            item.setLineTotal(lineTotal);
            allocateSupplierLots(item, product, quantity);
            invoice.getItems().add(item);

            subtotal = subtotal.add(base);
            gstTotal = gstTotal.add(gst);
        }

        BigDecimal discount = nvl(invoice.getDiscount());
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount negative nahi ho sakta");
        }
        BigDecimal total = subtotal.add(gstTotal).subtract(discount).max(BigDecimal.ZERO);
        BigDecimal paid = nvl(invoice.getPaidAmount());
        if (paid.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Paid amount negative nahi ho sakta");
        }
        if (paid.compareTo(total) > 0) {
            throw new IllegalArgumentException("Paid amount total se zyada nahi ho sakta");
        }
        BigDecimal due = total.subtract(paid).max(BigDecimal.ZERO);

        invoice.setSubtotal(subtotal);
        invoice.setGstTotal(gstTotal);
        invoice.setTotal(total);
        invoice.setPaidAmount(paid);
        invoice.setDueAmount(due);
        if (due.compareTo(BigDecimal.ZERO) > 0 && invoice.getPaymentMode() != PaymentMode.PARTIAL) {
            invoice.setPaymentMode(PaymentMode.CREDIT);
        }

        invoice.getItems().forEach(item -> item.getProduct().setQuantity(item.getProduct().getQuantity().subtract(item.getQuantity())));
        updateCustomerDue(invoice, due);
        return invoices.save(invoice);
    }

    private void allocateSupplierLots(InvoiceItem item, Product product, BigDecimal quantity) {
        BigDecimal remaining = quantity;
        List<Purchase> lots = purchases.findByProductIdAndRemainingQuantityGreaterThanOrderByPurchaseDateAscIdAsc(
                product.getId(), BigDecimal.ZERO);
        for (Purchase lot : lots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = nvl(lot.getRemainingQuantity());
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal consume = available.min(remaining);
            lot.setRemainingQuantity(available.subtract(consume));

            InvoiceItemLot itemLot = new InvoiceItemLot();
            itemLot.setInvoiceItem(item);
            itemLot.setPurchase(lot);
            itemLot.setSupplier(lot.getSupplier());
            itemLot.setQuantity(consume);
            itemLot.setPurchaseRate(nvl(lot.getRate()));
            itemLot.setNote("Auto FIFO supplier lot");
            item.getLots().add(itemLot);
            remaining = remaining.subtract(consume);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            InvoiceItemLot itemLot = new InvoiceItemLot();
            itemLot.setInvoiceItem(item);
            itemLot.setQuantity(remaining);
            itemLot.setPurchaseRate(nvl(product.getPurchasePrice()));
            itemLot.setNote("Legacy stock without supplier lot");
            item.getLots().add(itemLot);
        }
    }

    private void updateCustomerDue(Invoice invoice, BigDecimal due) {
        boolean hasMobile = invoice.getMobileNumber() != null && !invoice.getMobileNumber().isBlank();
        boolean hasName = invoice.getCustomerName() != null && !invoice.getCustomerName().isBlank();
        if (!hasMobile && !hasName) {
            return;
        }
        Customer customer = hasMobile
                ? customers.findFirstByMobileNumberOrderByIdAsc(invoice.getMobileNumber()).orElseGet(Customer::new)
                : customers.findFirstByNameIgnoreCaseOrderByIdAsc(invoice.getCustomerName()).orElseGet(Customer::new);
        customer.setName(invoice.getCustomerName());
        customer.setMobileNumber(invoice.getMobileNumber());
        customer.setAddress(invoice.getAddress());
        customer.setDueAmount(nvl(customer.getDueAmount()).add(due));
        customers.save(customer);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String productLabel(Product product) {
        if (product.getSize() == null || product.getSize().isBlank()) {
            return product.getName();
        }
        return product.getName() + " (" + product.getSize() + ")";
    }
}
