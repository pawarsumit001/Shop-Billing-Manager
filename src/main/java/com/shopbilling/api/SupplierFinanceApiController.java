package com.shopbilling.api;

import com.shopbilling.dto.ApiDtos.SupplierPaymentDto;
import com.shopbilling.dto.ApiDtos.SupplierPaymentRequest;
import com.shopbilling.dto.ApiDtos.SupplierSettlementDto;
import com.shopbilling.dto.ApiDtos.SupplierSettlementRequest;
import com.shopbilling.service.SupplierFinanceService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SupplierFinanceApiController {
    private final SupplierFinanceService supplierFinanceService;

    public SupplierFinanceApiController(SupplierFinanceService supplierFinanceService) {
        this.supplierFinanceService = supplierFinanceService;
    }

    @GetMapping("/supplier-payments")
    public List<SupplierPaymentDto> supplierPayments() {
        return supplierFinanceService.payments();
    }

    @PostMapping("/supplier-payments")
    public ResponseEntity<?> paySupplier(@RequestBody SupplierPaymentRequest request, Principal principal) {
        try {
            return ResponseEntity.ok(supplierFinanceService.pay(request, principal));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/supplier-settlements")
    public List<SupplierSettlementDto> supplierSettlements() {
        return supplierFinanceService.settlements();
    }

    @PostMapping("/supplier-settlements")
    public ResponseEntity<?> saveSupplierSettlement(@RequestBody SupplierSettlementRequest request, Principal principal) {
        try {
            return ResponseEntity.ok(supplierFinanceService.settle(request, principal));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
