package edu.cit.carcueva.inventory;

/**
 * Outcome of an inventory reservation attempt.
 */
public record ReservationResult(boolean success, String reason, InventoryItemView inventory) {

    public static ReservationResult approved(InventoryItemView inventory) {
        return new ReservationResult(true, "Reserved successfully", inventory);
    }

    public static ReservationResult rejected(String reason, InventoryItemView inventory) {
        return new ReservationResult(false, reason, inventory);
    }
}
