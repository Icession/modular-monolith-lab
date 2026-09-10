package edu.cit.carcueva.shop;

import edu.cit.carcueva.inventory.InventoryItemView;

public record OrderResponse(
        Long orderId,
        String status,
        String reason,
        InventoryItemView inventory
) {
}
