package edu.cit.carcueva.inventory;

import java.util.List;
import java.util.Optional;

/**
 * Public contract of the Inventory module. This is the ONLY type the
 * Order module (or any other module) is allowed to depend on. The
 * implementation (InventoryServiceImpl) is package-private, so nothing
 * outside this package can new-up, cast to, or otherwise reach around
 * this interface.
 */
public interface InventoryService {

    /**
     * Look up an inventory item by product id.
     */
    Optional<InventoryItemView> getItem(String productId);

    /**
     * All products with their current stock, for the inventory dashboard.
     */
    List<InventoryItemView> getAllItems();

    /**
     * Attempt to reserve `quantity` units of `productId`.
     * Rejects if the product doesn't exist or the requested quantity
     * exceeds current stock; otherwise decrements stock and approves.
     * May publish a LowStockEvent as a side effect if the resulting
     * stock falls below the configured threshold.
     */
    ReservationResult reserve(String productId, int quantity);

    /**
     * Returns `quantity` units of `productId` back to stock. Used when
     * an order is cancelled. Rejects only if the product itself doesn't
     * exist — there is no upper bound check, since restocking can't
     * over-draw anything.
     */
    ReservationResult restock(String productId, int quantity);
}
