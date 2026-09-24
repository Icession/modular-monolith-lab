package edu.cit.carcueva.supplier;

import java.util.Optional;
final class StatusTranslator {

    private StatusTranslator() {
    }

    static Optional<SupplierOrderStatus> toDomain(String legacyStatusCode) {
        if (legacyStatusCode == null) {
            return Optional.empty();
        }
        return switch (legacyStatusCode.trim()) {
            case "10" -> Optional.of(SupplierOrderStatus.SUBMITTED);   // Accepted
            case "20" -> Optional.of(SupplierOrderStatus.IN_PROGRESS); // Picking
            case "30" -> Optional.of(SupplierOrderStatus.SHIPPED);     // Shipped
            case "40" -> Optional.of(SupplierOrderStatus.DELIVERED);   // Delivered
            case "90" -> Optional.of(SupplierOrderStatus.CANCELLED);   // Not in the manual - first seen on PO-100073; never progresses
            default -> Optional.empty();
        };
    }
}