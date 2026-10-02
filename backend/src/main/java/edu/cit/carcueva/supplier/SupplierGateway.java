package edu.cit.carcueva.supplier;

import java.util.List;
import java.util.Optional;

public interface SupplierGateway {
    ReorderResult requestReorder(String productId, int unitsNeeded);

    List<SupplierOrderView> listOrders();

    Optional<String> supplierSkuFor(String productId);

    boolean hasPurchaseOrderInFlight(String productId);
}
