package com.example.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(0);

    public ProductService() {
        create(new Product(null, "Mechanical keyboard", new BigDecimal("129.00")));
        create(new Product(null, "4K webcam", new BigDecimal("199.00")));
        create(new Product(null, "USB-C dock", new BigDecimal("89.00")));
    }

    public List<Product> findAll() {
        return products.values().stream()
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
    }

    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(products.get(id));
    }

    public Product create(Product product) {
        Product saved = product.withId(idSequence.incrementAndGet());
        products.put(saved.id(), saved);
        return saved;
    }

    public long count() {
        return products.size();
    }
}
