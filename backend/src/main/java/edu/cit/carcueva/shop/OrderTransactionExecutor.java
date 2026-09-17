package edu.cit.carcueva.shop;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.carcueva.inventory.InventoryItemView;
import edu.cit.carcueva.inventory.InventoryService;
import edu.cit.carcueva.inventory.ReservationResult;

/**
 * Holds the actual @Transactional work for placing an order. Split out
 * from OrderService into its own Spring bean deliberately: calling a
 * @Transactional method on `this` from another method in the SAME class
 * bypasses Spring's proxy (self-invocation), so the transaction boundary
 * would silently not apply. Putting these methods on a separate
 * collaborator bean that OrderService calls through means the proxy is
 * actually in the call path, and @Transactional (and its REQUIRES_NEW
 * variant) really do take effect.
 */
@Component
class OrderTransactionExecutor {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    OrderTransactionExecutor(
            InventoryService inventoryService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Reserves every item and writes the order + order_items in one
     * shared transaction. If any reserve() call fails (a race condition,
     * since the caller already pre-validated stock), the exception rolls
     * back every reservation made earlier in this same loop AND the not-
     * yet-flushed order writes, atomically, because Order's writes and
     * Inventory's reserve() calls share one database transaction by
     * default @Transactional propagation (REQUIRED) in this monolith.
     */
    @Transactional
    OrderResponse reserveAndConfirm(List<OrderItemRequest> items) {
        List<InventoryItemView> updatedInventory = new ArrayList<>();

        for (OrderItemRequest item : items) {
            ReservationResult result = inventoryService.reserve(item.productId(), item.quantity());
            if (!result.success()) {
                throw new OrderReservationException(item.productId(), result.reason());
            }
            updatedInventory.add(result.inventory());
        }

        Order order = orderRepository.save(new Order("CONFIRMED", "All items reserved successfully"));

        List<OrderItemResult> itemResults = new ArrayList<>();
        for (OrderItemRequest item : items) {
            orderItemRepository.save(new OrderItem(order.getOrderId(), item.productId(), item.quantity()));
            itemResults.add(new OrderItemResult(item.productId(), item.quantity(), "CONFIRMED", null));
        }

        eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId(), "CONFIRMED",
                "Order " + order.getOrderId() + " confirmed"));

        return new OrderResponse(order.getOrderId(), "CONFIRMED", order.getReason(),
                itemResults, updatedInventory, order.getCreatedAt());
    }

    /**
     * Runs in its OWN new transaction (REQUIRES_NEW), independent of
     * whatever transaction the caller might already be in. This matters
     * for the race-condition path: reserveAndConfirm's transaction has
     * already rolled back by the time this runs, and we still need to
     * successfully persist the REJECTED order row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    OrderResponse persistRejectedOrder(OrderRequest request, List<OrderStockFailure> failures) {
        String combinedReason = failures.stream()
                .map(OrderStockFailure::reason)
                .reduce((a, b) -> a + "; " + b)
                .orElse("Order rejected");

        Order order = orderRepository.save(new Order("REJECTED", combinedReason));

        List<OrderItemResult> itemResults = new ArrayList<>();
        for (OrderItemRequest item : request.items()) {
            orderItemRepository.save(new OrderItem(order.getOrderId(), item.productId(), item.quantity()));

            String itemReason = failures.stream()
                    .filter(f -> f.productId().equals(item.productId()))
                    .map(OrderStockFailure::reason)
                    .findFirst()
                    .orElse("Rejected because another item in this order failed validation");

            itemResults.add(new OrderItemResult(item.productId(), item.quantity(), "REJECTED", itemReason));
        }

        eventPublisher.publishEvent(new OrderRejectedEvent(order.getOrderId(), combinedReason));

        return new OrderResponse(order.getOrderId(), "REJECTED", combinedReason,
                itemResults, List.of(), order.getCreatedAt());
    }
}
