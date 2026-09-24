package edu.cit.carcueva.supplier;

import java.util.List;

/**
 * The ONLY entry point other code uses to reorder stock from a supplier.
 *
 * It speaks our language: a product ID from our Inventory and a number of
 * units (single items) we need. Everything LegacySupply-specific - XML,
 * supplier SKUs, pack sizes, cases, session tokens, status codes - stays
 * behind this interface in package-private classes.
 */
public interface SupplierGateway {

    /**
     * Ask the supplier for at least `unitsNeeded` more units of `productId`.
     * Never throws because the supplier is down: if LegacySupply can't be
     * reached, the reorder is saved as PENDING and a scheduled job sends it
     * later.
     */
    ReorderResult requestReorder(String productId, int unitsNeeded);

    /** Every reorder we have on record, newest first. */
    List<SupplierOrderView> listOrders();
}
