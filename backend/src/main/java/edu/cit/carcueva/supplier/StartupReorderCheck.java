package edu.cit.carcueva.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import edu.cit.carcueva.inventory.InventoryItemView;
import edu.cit.carcueva.inventory.InventoryService;

@Component
class StartupReorderCheck {
    private static final Logger log = LoggerFactory.getLogger(StartupReorderCheck.class);

    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final int threshold;
    private final int targetStock;

    StartupReorderCheck(
            InventoryService inventoryService,
            SupplierGateway supplierGateway,
            @Value("${inventory.low-stock-threshold:5}") int threshold,
            @Value("${supplier.reorder-target-stock:20}") int targetStock) {
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.threshold = threshold;
        this.targetStock = targetStock;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void reorderLowStockOnStartup() {
        sleep(15000);
        for (InventoryItemView item : inventoryService.getAllItems()) {
            if (item.stock() < threshold && supplierGateway.supplierSkuFor(item.productId()).isPresent()) {
                ReorderResult result = supplierGateway.requestReorder(item.productId(),
                        Math.max(targetStock - item.stock(), 1));
                log.info("[Reorder] Startup check: {} has {} left -> {} ({})",
                        item.productId(), item.stock(), result.status(), result.message());
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
