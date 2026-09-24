package edu.cit.carcueva.inventory;

/**
 * "New stock physically arrived for this product." Owned by Inventory,
 * in Inventory's own terms: a product ID and a number of single units.
 *
 * The supplier module publishes it when a supplier order is delivered;
 * Inventory listens and restocks. Inventory never calls the supplier module
 * and doesn't know (or care) which supplier the stock came from.
 */
public record ReplenishmentReceivedEvent(String productId, int units, String reference) {
}
