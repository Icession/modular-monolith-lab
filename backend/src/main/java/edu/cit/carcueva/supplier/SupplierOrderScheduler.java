package edu.cit.carcueva.supplier;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The two background jobs. Both stop early when LegacySupply is clearly
 * down or refusing us, to stay inside the request quota.
 */
@Component
class SupplierOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderScheduler.class);

    private final SupplierOrderStore store;
    private final LegacySupplyGateway gateway;
    private final LegacySupplyClient client;

    SupplierOrderScheduler(SupplierOrderStore store, LegacySupplyGateway gateway, LegacySupplyClient client) {
        this.store = store;
        this.gateway = gateway;
        this.client = client;
    }


    @Scheduled(fixedDelayString = "${supplier.dispatch-interval-ms:20000}", initialDelay = 10000)
    public void dispatchPendingReorders() {
        if (client.isCoolingDown()) {
            return;
        }
        for (SupplierOrder pending : store.pendingReadyToSend(Instant.now().minusSeconds(15))) {
            SupplierOrder after = gateway.submit(pending.getId());
            if (after.getStatus() == SupplierOrderStatus.PENDING) {
                break; // still failing - supplier probably down, try again next run
            }
        }
    }


    @Scheduled(fixedDelayString = "${supplier.tracking-interval-ms:60000}", initialDelay = 30000)
    public void trackOpenOrders() {
        if (client.isCoolingDown()) {
            return;
        }
        for (SupplierOrder order : store.ordersToTrack()) {
            try {
                PurchaseOrderAck status = client.getOrder(order.getPoNumber());
                Optional<SupplierOrderStatus> ours = StatusTranslator.toDomain(status.statusCode());

                if (ours.isEmpty()) {
                    // A code the manual never mentioned: flag it, keep tracking (see INTEGRATION.md)
                    store.markNeedsAttention(order.getId(), "Unexpected supplier status code " + status.statusCode());
                    log.warn("[Tracking] {} returned unexpected status code {}", order.getPoNumber(), status.statusCode());
                } else if (ours.get() == SupplierOrderStatus.CANCELLED) {
                    // The stock will never arrive. Stop tracking it (no restock), then re-place the
                    // same need as a NEW reorder (new BuyerRef, new X-Request-Id) so we don't stay short.
                    store.markCancelled(order.getId(), "Cancelled by supplier (status " + status.statusCode() + ")");
                    ReorderResult replacement = gateway.requestReorder(order.getProductId(), order.getUnits());
                    store.markCancelled(order.getId(), "Cancelled by supplier (status " + status.statusCode()
                            + ") - replaced by reorder #" + replacement.reorderId());
                    log.warn("[Tracking] {} cancelled by supplier - replacement reorder #{} ({})",
                            order.getPoNumber(), replacement.reorderId(), replacement.status());
                } else if (ours.get() == SupplierOrderStatus.DELIVERED) {
                    store.markDelivered(order.getId());
                    log.info("[Tracking] {} delivered - {} units of {} restocked",
                            order.getPoNumber(), order.getUnits(), order.getProductId());
                } else {
                    store.updateTrackedStatus(order.getId(), ours.get());
                }
            } catch (LegacySupplyException e) {
                if (e.kind() == LegacySupplyException.Kind.NOT_FOUND) {
                    store.markFailed(order.getId(), "Supplier no longer knows this PO: " + e.describe());
                } else {
                    log.warn("[Tracking] Stopping this run: {}", e.describe());
                    break; // down / over quota - don't keep spending requests
                }
            }
        }
    }
}
