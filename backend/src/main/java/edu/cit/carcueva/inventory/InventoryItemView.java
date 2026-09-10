package edu.cit.carcueva.inventory;

/**
 * Read-only projection of an inventory item. This — not the JPA entity —
 * is what crosses the module boundary into the Order module, so Order
 * never depends on inventory's persistence details.
 */
public record InventoryItemView(String productId, String name, int stock) {
}
