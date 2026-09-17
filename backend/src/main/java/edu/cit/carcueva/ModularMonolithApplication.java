package edu.cit.carcueva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single Spring Boot application hosting two modules:
 *   - edu.cit.carcueva.inventory (Inventory module)
 *   - edu.cit.carcueva.shop      (Order module)
 *
 * Sits in the parent package so component scanning covers both
 * modules automatically.
 */
@SpringBootApplication
public class ModularMonolithApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModularMonolithApplication.class, args);
    }
}
