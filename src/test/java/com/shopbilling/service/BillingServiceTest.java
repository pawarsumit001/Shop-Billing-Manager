package com.shopbilling.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopbilling.model.Invoice;
import com.shopbilling.model.PaymentMode;
import com.shopbilling.model.Product;
import com.shopbilling.repository.CustomerRepository;
import com.shopbilling.repository.InvoiceRepository;
import com.shopbilling.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BillingServiceTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final BillingService service = new BillingService(products, customers, invoices);

    @Test
    void rejectsPaidAmountGreaterThanTotalWithoutChangingStock() {
        Product product = product();
        when(products.findById(1L)).thenReturn(Optional.of(product));

        Invoice invoice = new Invoice();
        invoice.setPaidAmount(new BigDecimal("150"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createInvoice(invoice, List.of(1L), List.of(BigDecimal.ONE)));

        assertEquals("Paid amount total se zyada nahi ho sakta", error.getMessage());
        assertEquals(new BigDecimal("5"), product.getQuantity());
        verify(invoices, never()).save(any());
    }

    @Test
    void createsPartialCreditBillAndReducesStock() {
        Product product = product();
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(invoices.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = new Invoice();
        invoice.setPaymentMode(PaymentMode.CASH);
        invoice.setPaidAmount(new BigDecimal("40"));

        Invoice saved = service.createInvoice(invoice, List.of(1L), List.of(BigDecimal.ONE));

        assertEquals(new BigDecimal("100"), saved.getTotal());
        assertEquals(new BigDecimal("60"), saved.getDueAmount());
        assertEquals(PaymentMode.CREDIT, saved.getPaymentMode());
        assertEquals(new BigDecimal("4"), product.getQuantity());
    }

    private Product product() {
        Product product = new Product();
        product.setName("Motor Pump");
        product.setQuantity(new BigDecimal("5"));
        product.setSellingPrice(new BigDecimal("100"));
        product.setPurchasePrice(new BigDecimal("70"));
        product.setGstPercent(BigDecimal.ZERO);
        return product;
    }
}
