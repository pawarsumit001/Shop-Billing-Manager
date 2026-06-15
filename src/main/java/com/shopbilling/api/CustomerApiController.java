package com.shopbilling.api;

import com.shopbilling.model.Customer;
import com.shopbilling.service.CustomerLedgerService;
import com.shopbilling.service.CustomerService;
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
public class CustomerApiController {
    private final CustomerService customerService;
    private final CustomerLedgerService customerLedgerService;

    public CustomerApiController(CustomerService customerService, CustomerLedgerService customerLedgerService) {
        this.customerService = customerService;
        this.customerLedgerService = customerLedgerService;
    }

    @GetMapping("/customers")
    public List<Customer> customers() {
        return customerService.findAll();
    }

    @PostMapping("/customers")
    public ResponseEntity<?> saveCustomer(@RequestBody Customer customer) {
        try {
            return ResponseEntity.ok(customerService.save(customer));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/customers/{id}")
    public ResponseEntity<?> deleteCustomer(@PathVariable Long id) {
        if (!customerService.exists(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            customerService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Customer deleted"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/customers/{id}/ledger")
    public ResponseEntity<?> customerLedger(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(customerLedgerService.ledger(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
