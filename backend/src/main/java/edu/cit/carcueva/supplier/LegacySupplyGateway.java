package edu.cit.carcueva.supplier;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.cit.carcueva.supplier.SupplierItemMapping.SupplierItem;

/**
 * The Anti-Corruption Layer itself: implements our SupplierGateway using
 * LegacySupply. Package-private - nobody outside this module can even name
 * this class; they only ever see SupplierGateway.
 *
 * `synchronized` so the async low-stock listener and the scheduled dispatcher
 * can never send the same reorder at the same moment.
 */
@Service
class LegacySupplyGateway implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyGateway.class);

    private final SupplierItemMapping mapping;
    private final SupplierOrderStore store;
    private final LegacySupplyClient client;

    LegacySupplyGateway(SupplierItemMapping mapping, SupplierOrderStore store, LegacySupplyClient client) {
        this.mapping = mapping;
        this.store = store;
        this.client = client;
    }

    @Override
    public synchronized ReorderResult requestReorder(String productId, int unitsNeeded) {
        if (unitsNeeded <= 0) {
            return ReorderResult.refused(productId, unitsNeeded, "Units needed must be greater than zero");
        }

        Optional<SupplierItem> item = mapping.find(productId);
        if (item.isEmpty()) {
            return ReorderResult.refused(productId, unitsNeeded,
                    "Product " + productId + " has no supplier item mapped (supplier.items)");
        }

        Optional<SupplierOrder> open = store.findOpenOrderFor(productId);
        if (open.isPresent()) {
            log.info("[Reorder] {} already has open reorder {} ({}); not placing another",
                    productId, open.get().getBuyerRef(), open.get().getStatus());
            return ReorderResult.from(open.get(), unitsNeeded, "A reorder for this product is already open");
        }

        int cases = item.get().casesFor(unitsNeeded);
        int units = cases * item.get().packSize();
        SupplierOrder created = store.createPending(productId, cases, units);
        log.info("[Reorder] {} needs {} units -> {} case(s) of {} = {} units, saved as {}",
                productId, unitsNeeded, cases, item.get().packSize(), units, created.getBuyerRef());

        SupplierOrder after = submit(created.getId());
        String message = switch (after.getStatus()) {
            case PENDING -> "Supplier unavailable - saved as PENDING, will be sent automatically";
            case FAILED, NEEDS_ATTENTION -> "Supplier refused the order: " + after.getLastError();
            default -> "Purchase order placed";
        };
        return ReorderResult.from(after, unitsNeeded, message);
    }

    @Override
    public List<SupplierOrderView> listOrders() {
        return store.all().stream().map(SupplierOrderView::from).toList();
    }

    synchronized SupplierOrder submit(Long id) {
        SupplierOrder order = store.find(id).orElseThrow();
        if (order.getStatus() != SupplierOrderStatus.PENDING || order.getPoNumber() != null) {
            return order;
        }
        if (client.isCoolingDown()) {
            return order;
        }

        Optional<SupplierItem> item = mapping.find(order.getProductId());
        if (item.isEmpty()) {
            store.markFailed(id, "No supplier item mapped for " + order.getProductId());
            return store.find(id).orElseThrow();
        }

        try {
            if (order.getAttempts() > 0) {
                Optional<PurchaseOrderAck> existing = client.findByBuyerRef(order.getBuyerRef());
                if (existing.isPresent()) {
                    log.info("[Reorder] {} already exists at supplier as {} - adopting it, not re-sending",
                            order.getBuyerRef(), existing.get().poNumber());
                    store.markSubmitted(id, existing.get().poNumber(), initialStatus(existing.get()),
                            "Found existing order by BuyerRef");
                    return store.find(id).orElseThrow();
                }
            }

            store.recordAttempt(id);
            PurchaseOrderAck ack = client.placeOrder(
                    item.get().supplierSku(), order.getCases(), order.getBuyerRef(), order.getRequestId());

            String note = ack.qty() > 0 && ack.qty() != order.getCases()
                    ? "Supplier acknowledged Qty " + ack.qty() + " but we ordered " + order.getCases()
                    : null;
            store.markSubmitted(id, ack.poNumber(), initialStatus(ack), note);
            log.info("[Reorder] {} placed as {}", order.getBuyerRef(), ack.poNumber());

        } catch (LegacySupplyException e) {
            switch (e.kind()) {
                // Supplier down / slow / over quota / session trouble / bad config:
                // keep it PENDING - never lose a reorder.
                case TRANSIENT, AUTH, RATE_LIMITED, CREDENTIALS -> {
                    store.recordRetryableFailure(id, e.describe());
                    log.warn("[Reorder] {} kept PENDING: {}", order.getBuyerRef(), e.describe());
                }
                case CONFLICT -> store.markNeedsAttention(id, e.describe());
                default -> store.markFailed(id, e.describe()); // REJECTED, NOT_FOUND
            }
        }
        return store.find(id).orElseThrow();
    }

    private static SupplierOrderStatus initialStatus(PurchaseOrderAck ack) {
        SupplierOrderStatus status = StatusTranslator.toDomain(ack.statusCode()).orElse(SupplierOrderStatus.SUBMITTED);
        return status == SupplierOrderStatus.DELIVERED ? SupplierOrderStatus.SUBMITTED : status;
    }
}
