package edu.cit.carcueva.shop;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderItemRequest(
        @NotBlank String productId,
        @Min(1) int quantity
) {
}
