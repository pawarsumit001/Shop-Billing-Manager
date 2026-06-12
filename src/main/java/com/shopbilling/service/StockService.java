package com.shopbilling.service;

import com.shopbilling.model.Customer;
import com.shopbilling.model.DuePayment;
import com.shopbilling.model.Invoice;
import com.shopbilling.model.PaymentMode;
import com.shopbilling.model.Product;
import com.shopbilling.model.Purchase;
import com.shopbilling.model.ReturnRecord;
import com.shopbilling.repository.CustomerRepository;
import com.shopbilling.repository.DuePaymentRepository;
import com.shopbilling.repository.InvoiceRepository;
import com.shopbilling.repository.ProductRepository;
import com.shopbilling.repository.PurchaseRepository;
import com.shopbilling.repository.ReturnRecordRepository;
import com.shopbilling.repository.SupplierRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class StockService {
    private final ProductRepository products;
    private final PurchaseRepository purchases;
    private final ReturnRecordRepository returns;
    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final DuePaymentRepository duePayments;
    private final SupplierRepository suppliers;

    public StockService(ProductRepository products, PurchaseRepository purchases, ReturnRecordRepository returns,
                        InvoiceRepository invoices, CustomerRepository customers, DuePaymentRepository duePayments,
                        SupplierRepository suppliers) {
        this.products = products;
        this.purchases = purchases;
        this.returns = returns;
        this.invoices = invoices;
        this.customers = customers;
        this.duePayments = duePayments;
        this.suppliers = suppliers;
    }

    @Transactional
    public Purchase recordPurchase(Purchase purchase) {
        if (purchase.getProduct() == null || purchase.getProduct().getId() == null) {
            throw new IllegalArgumentException("Product select karna zaroori hai");
        }
        if (purchase.getQuantity() == null || purchase.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Purchase quantity 0 se zyada honi chahiye");
        }
        if (purchase.getRate() == null || purchase.getRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Purchase rate negative nahi ho sakta");
        }
        Product product = products.findById(purchase.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        purchase.setProduct(product);
        if (purchase.getSupplier() != null && purchase.getSupplier().getId() != null) {
            var supplier = suppliers.findById(purchase.getSupplier().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
            purchase.setSupplier(supplier);
            product.setSupplier(supplier.getName());
        }
        purchase.setTotal(purchase.getQuantity().multiply(purchase.getRate()));
        purchase.setRemainingQuantity(purchase.getQuantity());
        product.setQuantity(product.getQuantity().add(purchase.getQuantity()));
        product.setPurchasePrice(purchase.getRate());
        return purchases.save(purchase);
    }

    @Transactional
    public ReturnRecord recordReturn(ReturnRecord returnRecord) {
        if (returnRecord.getProduct() == null || returnRecord.getProduct().getId() == null) {
            throw new IllegalArgumentException("Product select karna zaroori hai");
        }
        if (returnRecord.getQuantity() == null || returnRecord.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Return quantity 0 se zyada honi chahiye");
        }
        if (returnRecord.getRefundAmount() != null && returnRecord.getRefundAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Refund amount negative nahi ho sakta");
        }
        Product product = products.findById(returnRecord.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        product.setQuantity(product.getQuantity().add(returnRecord.getQuantity()));
        returnRecord.setProduct(product);
        if (returnRecord.getRefundAmount() == null) {
            returnRecord.setRefundAmount(BigDecimal.ZERO);
        }
        if (returnRecord.getInvoice() != null && returnRecord.getInvoice().getId() != null) {
            Invoice invoice = invoices.findById(returnRecord.getInvoice().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
            returnRecord.setInvoice(invoice);
            adjustDueForReturn(invoice, returnRecord.getRefundAmount());
        }
        return returns.save(returnRecord);
    }

    private void adjustDueForReturn(Invoice invoice, BigDecimal refundAmount) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Customer customer = findInvoiceCustomer(invoice);
        if (customer == null || customer.getDueAmount() == null || customer.getDueAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal beforeDue = customer.getDueAmount();
        BigDecimal adjustment = refundAmount.min(beforeDue);
        BigDecimal afterDue = beforeDue.subtract(adjustment);
        customer.setDueAmount(afterDue);
        customers.save(customer);

        BigDecimal invoiceDue = invoice.getDueAmount() == null ? BigDecimal.ZERO : invoice.getDueAmount();
        invoice.setDueAmount(invoiceDue.subtract(adjustment).max(BigDecimal.ZERO));
        invoices.save(invoice);

        DuePayment payment = new DuePayment();
        payment.setCustomer(customer);
        payment.setAmount(adjustment);
        payment.setBeforeDue(beforeDue);
        payment.setAfterDue(afterDue);
        payment.setPaymentMode(PaymentMode.CREDIT);
        payment.setNote("Return adjustment for invoice #" + invoice.getId());
        payment.setReceivedBy("system");
        duePayments.save(payment);
    }

    private Customer findInvoiceCustomer(Invoice invoice) {
        if (invoice.getMobileNumber() != null && !invoice.getMobileNumber().isBlank()) {
            return customers.findFirstByMobileNumberOrderByIdAsc(invoice.getMobileNumber()).orElse(null);
        }
        if (invoice.getCustomerName() != null && !invoice.getCustomerName().isBlank()) {
            return customers.findFirstByNameIgnoreCaseOrderByIdAsc(invoice.getCustomerName()).orElse(null);
        }
        return null;
    }
}
