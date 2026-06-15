package com.shopbilling.api;

import com.shopbilling.dto.ApiDtos.DuePaymentDto;
import com.shopbilling.dto.ApiDtos.DuePaymentRequest;
import com.shopbilling.service.DuePaymentService;
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
public class DuePaymentApiController {
    private final DuePaymentService duePaymentService;

    public DuePaymentApiController(DuePaymentService duePaymentService) {
        this.duePaymentService = duePaymentService;
    }

    @GetMapping("/due-payments")
    public List<DuePaymentDto> duePayments() {
        return duePaymentService.findAll();
    }

    @PostMapping("/due-payments")
    public ResponseEntity<?> receiveDuePayment(@RequestBody DuePaymentRequest request, Principal principal) {
        try {
            return ResponseEntity.ok(duePaymentService.receive(request, principal));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
