package edu.cit.carcueva.supplier;

/**
 * What SupplierGateway.requestReorder returns - in our own terms only.
 * `unitsOrdered` can be higher than `unitsNeeded` because the supplier
 * only sells whole packs; it never mentions packs or cases.
 */
public record ReorderResult(
        Long reorderId,
        String productId,
        int unitsNeeded,
        int unitsOrdered,
        SupplierOrderStatus status,
        String message
) {

    static ReorderResult from(SupplierOrder order, int unitsNeeded, String message) {
        return new ReorderResult(order.getId(), order.getProductId(), unitsNeeded,
                order.getUnits(), order.getStatus(), message);
    }

    static ReorderResult refused(String productId, int unitsNeeded, String message) {
        return new ReorderResult(null, productId, unitsNeeded, 0, SupplierOrderStatus.FAILED, message);
    }
}
