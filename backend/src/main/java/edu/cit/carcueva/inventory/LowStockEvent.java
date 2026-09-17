package edu.cit.carcueva.inventory;

/**
 * Published by InventoryServiceImpl whenever a successful reservation
 * leaves a product's stock below the configured low-stock threshold.
 *
 * This is a public type on purpose: it is Inventory's half of the public
 * "event contract" that the Notification module is allowed to depend on.
 * Notification imports this class directly, but never InventoryService
 * or InventoryServiceImpl.
 */
public record LowStockEvent(String productId, String name, int remainingStock, int threshold) {
}
