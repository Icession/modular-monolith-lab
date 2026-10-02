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

    @Transactional
    OrderResponse persistBackorder(OrderRequest request, String reason) {
        Order order = orderRepository.save(new Order("BACKORDERED", reason));

        List<OrderItemResult> itemResults = new ArrayList<>();
        for (OrderItemRequest item : request.items()) {
            orderItemRepository.save(new OrderItem(order.getOrderId(), item.productId(), item.quantity()));
            itemResults.add(new OrderItemResult(item.productId(), item.quantity(), "BACKORDERED", null));
        }

        return new OrderResponse(order.getOrderId(), "BACKORDERED", reason,
                itemResults, List.of(), order.getCreatedAt());
    }

    @Transactional
    OrderResponse fulfilBackorder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        if (!"BACKORDERED".equals(order.getStatus())) {
            return toResponse(order, items, List.of());
        }

        List<InventoryItemView> updatedInventory = new ArrayList<>();
        for (OrderItem item : items) {
            ReservationResult result = inventoryService.reserve(item.getProductId(), item.getQuantity());
            if (!result.success()) {
                throw new OrderReservationException(item.getProductId(), result.reason());
            }
            updatedInventory.add(result.inventory());
        }

        order.setStatus("CONFIRMED");
        order.setReason("Backorder filled after supplier delivery");
        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderPlacedEvent(saved.getOrderId(), "CONFIRMED",
                "Order " + saved.getOrderId() + " confirmed (backorder filled)"));

        return toResponse(saved, items, updatedInventory);
    }

    @Transactional(readOnly = true)
    OrderResponse load(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return toResponse(order, orderItemRepository.findByOrderId(orderId), List.of());
    }

    private OrderResponse toResponse(Order order, List<OrderItem> items, List<InventoryItemView> inventory) {
        List<OrderItemResult> itemResults = items.stream()
                .map(oi -> new OrderItemResult(oi.getProductId(), oi.getQuantity(), order.getStatus(), null))
                .toList();
        return new OrderResponse(order.getOrderId(), order.getStatus(), order.getReason(),
                itemResults, inventory, order.getCreatedAt());
    }
}
