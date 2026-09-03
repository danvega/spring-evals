package com.example.inventory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }

    @Bean
    CommandLineRunner seedCatalog(ProductRepository repository) {
        return args -> {
            repository.save(new Product("Ceramic bearing kit", "SKU-1001", 42));
            repository.save(new Product("Anodized frame", "SKU-1002", 7));
            repository.save(new Product("Torque wrench", "SKU-1003", 18));
        };
    }
}
