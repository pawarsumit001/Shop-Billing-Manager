package com.shopbilling.api;

import com.shopbilling.dto.ApiDtos.SupplierDto;
import com.shopbilling.model.Supplier;
import com.shopbilling.service.SupplierLedgerService;
import com.shopbilling.service.SupplierService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SupplierApiController {
    private final SupplierService supplierService;
    private final SupplierLedgerService supplierLedgerService;

    public SupplierApiController(SupplierService supplierService, SupplierLedgerService supplierLedgerService) {
        this.supplierService = supplierService;
        this.supplierLedgerService = supplierLedgerService;
    }

    @GetMapping("/suppliers")
    public List<SupplierDto> suppliers() {
        return supplierService.findAll();
    }

    @PostMapping("/suppliers")
    public ResponseEntity<?> saveSupplier(@RequestBody Supplier supplier) {
        try {
            return ResponseEntity.ok(supplierService.save(supplier));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<?> deleteSupplier(@PathVariable Long id) {
        if (!supplierService.exists(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            supplierService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Supplier deleted"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Supplier purchase/product mein use ho chuka hai, delete nahi kar sakte"));
        }
    }

    @GetMapping("/suppliers/{id}/ledger")
    public ResponseEntity<?> supplierLedger(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(supplierLedgerService.ledger(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
