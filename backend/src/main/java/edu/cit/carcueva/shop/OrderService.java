package edu.cit.carcueva.shop;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.carcueva.inventory.InventoryService;

/**
 * Order module's public service. Depends only on InventoryService - the
 * public interface - never on InventoryServiceImpl or InventoryRepository.
 *
 * The actual @Transactional reservation/persistence work lives in
 * OrderTransactionExecutor, a separate bean - see that class for why
 * (Spring proxy self-invocation).
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderTransactionExecutor transactionExecutor;

    public OrderService(
            InventoryService inventoryService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderTransactionExecutor transactionExecutor) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.transactionExecutor = transactionExecutor;
    }

    /**
     * All-or-nothing multi-item order placement.
     *
     * Step 1 (here, no transaction) validates every line item against
     * current stock without reserving anything - so a rejection never
     * touches the database at all. Only if every item passes does step 2
     * (in OrderTransactionExecutor) attempt to actually reserve them,
     * inside one database transaction shared with the Order/order_items
     * write. If a race condition causes a reservation to fail anyway
     * (stock consumed by a concurrent order between validation and
     * reservation), that transaction rolls back every reservation made
     * earlier in this same order atomically - see the README reflection
     * for what has to be rebuilt to keep that guarantee once Order and
     * Inventory become two separate services over a network.
     */
    public OrderResponse placeOrder(OrderRequest request) {
        List<OrderStockFailure> failures = validateStock(request.items());

        if (!failures.isEmpty()) {
            return transactionExecutor.persistRejectedOrder(request, failures);
        }

        try {
            return transactionExecutor.reserveAndConfirm(request.items());
        } catch (OrderReservationException ex) {
            OrderStockFailure raceFailure = new OrderStockFailure(ex.failedProductId(), ex.getMessage());
            return transactionExecutor.persistRejectedOrder(request, List.of(raceFailure));
        }
    }

    private List<OrderStockFailure> validateStock(List<OrderItemRequest> items) {
        List<OrderStockFailure> failures = new ArrayList<>();

        for (OrderItemRequest item : items) {
            var maybeInventory = inventoryService.getItem(item.productId());

            if (maybeInventory.isEmpty()) {
                failures.add(new OrderStockFailure(item.productId(),
                        "Product " + item.productId() + " does not exist"));
                continue;
            }

            int stock = maybeInventory.get().stock();
            if (item.quantity() > stock) {
                failures.add(new OrderStockFailure(item.productId(),
                        "Requested quantity (" + item.quantity() + ") exceeds available stock (" + stock + ")"));
            }
        }

        return failures;
    }

    public List<OrderResponse> getOrderHistory() {
        return orderRepository.findAll().stream()
                .map(order -> {
                    List<OrderItemResult> items = orderItemRepository.findByOrderId(order.getOrderId()).stream()
                            .map(oi -> new OrderItemResult(oi.getProductId(), oi.getQuantity(), order.getStatus(), null))
                            .toList();
                    return new OrderResponse(order.getOrderId(), order.getStatus(), order.getReason(),
                            items, List.of(), order.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if ("CANCELLED".equals(order.getStatus())) {
            throw new OrderAlreadyCancelledException(orderId);
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        // Only a CONFIRMED order actually holds a reservation to give back -
        // a REJECTED order never reserved any stock in the first place.
        if ("CONFIRMED".equals(order.getStatus())) {
            for (OrderItem item : items) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus("CANCELLED");
        Order saved = orderRepository.save(order);

        List<OrderItemResult> itemResults = items.stream()
                .map(oi -> new OrderItemResult(oi.getProductId(), oi.getQuantity(), "CANCELLED", null))
                .toList();

        return new OrderResponse(saved.getOrderId(), saved.getStatus(), saved.getReason(),
                itemResults, List.of(), saved.getCreatedAt());
    }
}
