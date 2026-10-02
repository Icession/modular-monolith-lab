package edu.cit.carcueva.channel;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import edu.cit.carcueva.inventory.InventoryItemView;
import edu.cit.carcueva.inventory.InventoryService;
import edu.cit.carcueva.supplier.ReorderResult;
import edu.cit.carcueva.supplier.SupplierGateway;
import edu.cit.carcueva.supplier.SupplierOrderStatus;

@Component
class SupplyCheck {
    private static final Logger log = LoggerFactory.getLogger(SupplyCheck.class);

    private static final Set<SupplierOrderStatus> ON_ITS_WAY = EnumSet.of(
            SupplierOrderStatus.SUBMITTED, SupplierOrderStatus.IN_PROGRESS, SupplierOrderStatus.SHIPPED);

    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final int extraUnits;

    SupplyCheck(InventoryService inventoryService, SupplierGateway supplierGateway,
                @Value("${channel.backorder-extra-units:20}") int extraUnits) {
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.extraUnits = extraUnits;
    }

    Optional<Map<String, Integer>> shortfallsIfAllKnown(Map<String, Integer> quantities) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            Optional<InventoryItemView> item = inventoryService.getItem(entry.getKey());
            if (item.isEmpty()) {
                return Optional.empty();
            }
            int stock = item.get().stock();
            if (entry.getValue() > stock) {
                missing.put(entry.getKey(), entry.getValue() - stock);
            }
        }
        return Optional.of(missing);
    }

    boolean allKnown(Map<String, Integer> quantities) {
        return quantities.keySet().stream().allMatch(sku -> inventoryService.getItem(sku).isPresent());
    }

    Map<String, Integer> shortfalls(Map<String, Integer> quantities) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        quantities.forEach((sku, qty) -> {
            Optional<InventoryItemView> item = inventoryService.getItem(sku);
            int stock = item.map(InventoryItemView::stock).orElse(0);
            if (qty > stock) {
                missing.put(sku, qty - stock);
            }
        });
        return missing;
    }

    boolean restockOnItsWay(Map<String, Integer> missing, boolean mayPlaceReorder) {
        for (Map.Entry<String, Integer> entry : missing.entrySet()) {
            String sku = entry.getKey();
            if (supplierGateway.hasPurchaseOrderInFlight(sku)) {
                continue;
            }
            if (!mayPlaceReorder || supplierGateway.supplierSkuFor(sku).isEmpty()) {
                return false;
            }
            ReorderResult result = supplierGateway.requestReorder(sku, entry.getValue() + extraUnits);
            log.info("[Backorder] Asked supplier for {} units of {}: {} ({})",
                    entry.getValue() + extraUnits, sku, result.status(), result.message());
            if (!ON_ITS_WAY.contains(result.status())) {
                return false;
            }
        }
        return true;
    }
}
