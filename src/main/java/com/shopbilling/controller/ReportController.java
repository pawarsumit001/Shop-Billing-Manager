package com.shopbilling.controller;

import com.shopbilling.service.DocumentService;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private final DocumentService documentService;

    public ReportController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/stock.xlsx")
    public ResponseEntity<byte[]> stockExcel() throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stock-report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(documentService.stockExcel());
    }
}
