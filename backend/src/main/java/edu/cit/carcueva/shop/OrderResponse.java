package edu.cit.carcueva.shop;

import java.time.Instant;
import java.util.List;

import edu.cit.carcueva.inventory.InventoryItemView;

public record OrderResponse(
        Long orderId,
        String status,
        String reason,
        List<OrderItemResult> items,
        List<InventoryItemView> inventory,
        Instant createdAt
) {
}
