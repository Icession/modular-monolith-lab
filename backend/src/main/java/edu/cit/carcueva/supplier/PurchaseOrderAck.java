package edu.cit.carcueva.supplier;

/**
 * LegacySupply's PurchaseOrderAck / PurchaseOrderStatus document, read into
 * Java. Package-private: raw supplier fields (StatusCode, Uom...) stop here.
 */
record PurchaseOrderAck(
        String poNumber,
        String statusCode,
        String supplierSku,
        int qty,
        String uom,
        String buyerRef
) {
}
