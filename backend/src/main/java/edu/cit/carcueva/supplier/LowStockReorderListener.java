package edu.cit.carcueva.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import edu.cit.carcueva.inventory.LowStockEvent;

/**
 * The Lab 2 auto-reorder rule, now placing a real reorder through
 * SupplierGateway instead of only logging.
 *
 * AFTER_COMMIT: only reorder if the customer order that caused low stock
 *   actually committed (a rolled-back order shouldn't trigger a purchase).
 * @Async: runs on a background thread, so a slow or down LegacySupply
 *   never delays the customer's order response.
 *
 * Tops the product back up to supplier.reorder-target-stock units.
 */
@Component
class LowStockReorderListener {

    private static final Logger log = LoggerFactory.getLogger(LowStockReorderListener.class);

    private final SupplierGateway supplierGateway;
    private final int targetStock;

    LowStockReorderListener(
            SupplierGateway supplierGateway,
            @Value("${supplier.reorder-target-stock:20}") int targetStock) {
        this.supplierGateway = supplierGateway;
        this.targetStock = targetStock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLowStock(LowStockEvent event) {
        int unitsNeeded = Math.max(targetStock - event.remainingStock(), 1);
        ReorderResult result = supplierGateway.requestReorder(event.productId(), unitsNeeded);
        log.info("[Reorder] Low stock on {} ({} left): {} -> {}",
                event.productId(), event.remainingStock(), result.status(), result.message());
    }
}
