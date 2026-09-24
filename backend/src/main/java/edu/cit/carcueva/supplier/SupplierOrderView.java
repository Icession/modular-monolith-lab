package edu.cit.carcueva.supplier;

import java.time.Instant;

/**
 * Read-only view of one row of supplier_orders, for GET /api/supplier/orders
 * (debugging and the frontend panel). Lives in the supplier module and is
 * never imported by Order or Inventory.
 */
public record SupplierOrderView(
        Long id,
        String productId,
        String buyerRef,
        String requestId,
        String poNumber,
        int cases,
        int units,
        SupplierOrderStatus status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {

    static SupplierOrderView from(SupplierOrder o) {
        return new SupplierOrderView(o.getId(), o.getProductId(), o.getBuyerRef(), o.getRequestId(),
                o.getPoNumber(), o.getCases(), o.getUnits(), o.getStatus(), o.getAttempts(),
                o.getLastError(), o.getCreatedAt(), o.getUpdatedAt());
    }
}
