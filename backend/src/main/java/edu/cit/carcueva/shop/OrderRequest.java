package edu.cit.carcueva.shop;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record OrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items
) {
}
