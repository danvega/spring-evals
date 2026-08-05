package com.example.orders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(0);
    private final JsonMapper jsonMapper;

    public OrderService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        create(new Order(null, "Grace Hopper", LocalDate.of(2026, 7, 15), OrderStatus.SHIPPED, new BigDecimal("249.99")));
        create(new Order(null, "Alan Turing", LocalDate.of(2026, 7, 28), OrderStatus.PENDING, new BigDecimal("89.50")));
    }

    public List<Order> findAll() {
        return orders.values().stream()
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
    }

    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }

    public Order create(Order order) {
        Order saved = order.withId(idSequence.incrementAndGet());
        orders.put(saved.id(), saved);
        return saved;
    }

    /**
     * Serializes an order for the export endpoint. Uses the auto-configured
     * JsonMapper so the export matches the public API contract exactly.
     * Jackson 3 throws unchecked exceptions, so no try/catch is needed.
     */
    public String toJson(Order order) {
        return jsonMapper.writeValueAsString(order);
    }
}
