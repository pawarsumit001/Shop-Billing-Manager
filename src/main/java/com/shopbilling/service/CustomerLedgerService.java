package com.shopbilling.service;

import com.shopbilling.dto.ApiDtos.CustomerLedgerDto;
import com.shopbilling.dto.ApiDtos.LedgerEntryDto;
import com.shopbilling.dto.ApiSupport;
import com.shopbilling.model.Customer;
import com.shopbilling.model.Invoice;
import com.shopbilling.repository.CustomerRepository;
import com.shopbilling.repository.DuePaymentRepository;
import com.shopbilling.repository.InvoiceRepository;
import com.shopbilling.repository.ReturnRecordRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class CustomerLedgerService {
    private final CustomerRepository customers;
    private final DuePaymentRepository duePayments;
    private final InvoiceRepository invoices;
    private final ReturnRecordRepository returns;

    public CustomerLedgerService(CustomerRepository customers, DuePaymentRepository duePayments,
                                 InvoiceRepository invoices, ReturnRecordRepository returns) {
        this.customers = customers;
        this.duePayments = duePayments;
        this.invoices = invoices;
        this.returns = returns;
    }

    @Transactional
    public CustomerLedgerDto ledger(Long id) {
        Customer customer = customers.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
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
            if (invoice == null || !matchesCustomer(invoice, mobile, name)) {
                return;
            }
            entries.add(new LedgerEntryDto(returnRecord.getId(), "RETURN", String.valueOf(returnRecord.getReturnedAt()),
                    returnRecord.getReason(), returnRecord.getRefundAmount(), returnRecord.getRefundAmount(),
                    BigDecimal.ZERO, ""));
        });
        entries.sort((a, b) -> b.date().compareTo(a.date()));
        return new CustomerLedgerDto(customer, ApiSupport.nvl(customer.getDueAmount()), entries);
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
        boolean matchName = !name.isBlank()
                && name.equalsIgnoreCase(invoice.getCustomerName() == null ? "" : invoice.getCustomerName());
        return matchMobile || matchName;
    }
}
