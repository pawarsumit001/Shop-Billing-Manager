package com.shopbilling.api;

import com.shopbilling.dto.ApiDtos.AppSettingsDto;
import com.shopbilling.dto.ApiDtos.UserDto;
import com.shopbilling.dto.ApiDtos.UserRequest;
import com.shopbilling.model.AppUser;
import com.shopbilling.repository.AppUserRepository;
import com.shopbilling.repository.CustomerRepository;
import com.shopbilling.repository.InvoiceRepository;
import com.shopbilling.repository.ProductRepository;
import com.shopbilling.repository.PurchaseRepository;
import com.shopbilling.repository.StockAdjustmentRepository;
import com.shopbilling.service.AppSettingsService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AdminApiController {
    private final AppUserRepository users;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final PurchaseRepository purchases;
    private final StockAdjustmentRepository stockAdjustments;
    private final PasswordEncoder passwordEncoder;
    private final AppSettingsService appSettings;

    public AdminApiController(AppUserRepository users, ProductRepository products,
                              CustomerRepository customers, InvoiceRepository invoices, PurchaseRepository purchases,
                              StockAdjustmentRepository stockAdjustments, PasswordEncoder passwordEncoder,
                              AppSettingsService appSettings) {
        this.users = users;
        this.products = products;
        this.customers = customers;
        this.invoices = invoices;
        this.purchases = purchases;
        this.stockAdjustments = stockAdjustments;
        this.passwordEncoder = passwordEncoder;
        this.appSettings = appSettings;
    }

    @GetMapping("/users")
    public List<UserDto> users() {
        return users.findAll().stream().map(UserDto::from).toList();
    }

    @PostMapping("/users")
    public ResponseEntity<?> saveUser(@RequestBody UserRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username required hai"));
        }
        if (request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password required hai"));
        }
        AppUser user = new AppUser();
        user.setName(request.name());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setActive(request.active());
        return ResponseEntity.ok(UserDto.from(users.save(user)));
    }

    @GetMapping("/settings")
    public AppSettingsDto settings() {
        return appSettings.readSettings();
    }

    @PostMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody AppSettingsDto request) {
        appSettings.saveSettings(request);
        return ResponseEntity.ok(appSettings.readSettings());
    }

    @GetMapping("/backup")
    public Map<String, Object> backupStatus() {
        return Map.of(
                "backupPath", appSettings.setting("backupPath", "data/backups"),
                "products", products.count(),
                "customers", customers.count(),
                "invoices", invoices.count(),
                "purchases", purchases.count(),
                "stockAdjustments", stockAdjustments.count(),
                "message", "Backup ke liye PostgreSQL/Neon backup export aur invoice PDFs ko safe drive par copy karein");
    }

}
