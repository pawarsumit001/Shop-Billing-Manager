package com.shopbilling.api;

import com.shopbilling.dto.ApiDtos.LoginRequest;
import com.shopbilling.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemApiController {
    private static final Logger log = LoggerFactory.getLogger(SystemApiController.class);

    private final AppUserRepository users;
    private final AuthenticationManager authenticationManager;

    public SystemApiController(AppUserRepository users, AuthenticationManager authenticationManager) {
        this.users = users;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "ShopBilling Backend", "time", String.valueOf(LocalDateTime.now()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        try {
            log.info("Login attempt username={}", request == null ? "" : request.username());
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            servletRequest.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());
            String role = users.findByUsername(authentication.getName())
                    .map(user -> user.getRole().name())
                    .orElse("STAFF");
            log.info("Login success username={} role={}", authentication.getName(), role);
            return ResponseEntity.ok(Map.of("username", authentication.getName(), "role", role));
        } catch (Exception ex) {
            log.warn("Login failed username={} reason={}", request == null ? "" : request.username(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid username or password"));
        }
    }

    @GetMapping("/me")
    public Map<String, String> me(Principal principal) {
        String username = principal == null ? "" : principal.getName();
        String role = users.findByUsername(username)
                .map(user -> user.getRole().name())
                .orElse("STAFF");
        return Map.of("username", username, "role", role);
    }
}
