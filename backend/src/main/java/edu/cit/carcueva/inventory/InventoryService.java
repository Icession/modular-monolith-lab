package edu.cit.carcueva.inventory;

import java.util.Optional;

/**
 * Public contract of the Inventory module. This is the ONLY type the
 * Order module is allowed to depend on. The implementation
 * (InventoryServiceImpl) is package-private, so nothing outside this
 * package can new-up, cast to, or otherwise reach around this interface.
 */
public interface InventoryService {

    /**
     * Look up an inventory item by product id.
     */
    Optional<InventoryItemView> getItem(String productId);

    /**
     * Attempt to reserve `quantity` units of `productId`.
     * Rejects if the product doesn't exist or the requested quantity
     * exceeds current stock; otherwise decrements stock and approves.
     */
    ReservationResult reserve(String productId, int quantity);
}
