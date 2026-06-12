package com.shopbilling.controller;

import com.shopbilling.repository.InvoiceRepository;
import com.shopbilling.service.DocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/billing")
public class BillingController {
    private final InvoiceRepository invoices;
    private final DocumentService documentService;

    public BillingController(InvoiceRepository invoices, DocumentService documentService) {
        this.invoices = invoices;
        this.documentService = documentService;
    }

    @GetMapping("/invoice.pdf")
    public ResponseEntity<byte[]> invoicePdf(@RequestParam Long id) {
        if (!invoices.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        byte[] pdf = documentService.invoicePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
