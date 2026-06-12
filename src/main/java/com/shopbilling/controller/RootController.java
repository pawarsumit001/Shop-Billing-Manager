package com.shopbilling.controller;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {
    @Value("${app.frontend.url:http://127.0.0.1:5173}")
    private String frontendUrl;

    @GetMapping("/")
    public ResponseEntity<Void> root() {
        return ResponseEntity.status(302).location(URI.create(frontendUrl)).build();
    }
}
