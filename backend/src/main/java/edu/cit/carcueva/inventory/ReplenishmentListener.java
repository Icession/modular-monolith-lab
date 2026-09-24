package edu.cit.carcueva.inventory;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Adds delivered stock to inventory. Runs synchronously inside the same
 * transaction that marks the supplier order DELIVERED, so "order marked
 * delivered" and "stock added" commit together or not at all - a delivery
 * can't be counted twice or lost.
 */
@Component
class ReplenishmentListener {

    private final InventoryService inventoryService;

    ReplenishmentListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void onReplenishmentReceived(ReplenishmentReceivedEvent event) {
        inventoryService.restock(event.productId(), event.units());
    }
}
