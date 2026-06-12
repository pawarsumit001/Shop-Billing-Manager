package com.shopbilling.api;

import com.shopbilling.model.Product;
import com.shopbilling.repository.ProductRepository;
import com.shopbilling.util.ProductFilters;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {
    private static final Logger log = LoggerFactory.getLogger(ProductApiController.class);

    private final ProductRepository products;

    public ProductApiController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping
    public List<Product> products() {
        return products.findAll().stream().filter(ProductFilters::isValid).toList();
    }

    @PostMapping
    public ResponseEntity<?> saveProduct(@RequestBody Product product) {
        if (product.getName() == null || product.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Product name required hai"));
        }
        Product saved = products.save(product);
        log.info("Product saved id={} name={} stock={} purchasePrice={} sellingPrice={}",
                saved.getId(), saved.getName(), saved.getQuantity(), saved.getPurchasePrice(), saved.getSellingPrice());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        if (!products.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            products.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Product deleted"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Product bill/purchase mein use ho chuka hai, delete nahi kar sakte"));
        }
    }
}
