package edu.cit.carcueva.shop;

import org.springframework.stereotype.Service;

import edu.cit.carcueva.inventory.InventoryService;
import edu.cit.carcueva.inventory.ReservationResult;

/**
 * Order module's service. It depends on InventoryService — the public
 * interface only. It has no visibility of InventoryServiceImpl or
 * InventoryRepository (both package-private inside edu.cit.carcueva.inventory),
 * so this in-process call is the same shape as a remote call would be:
 * a request goes across a defined interface and a result comes back.
 * There is just no network hop, no serialization, and no partial-failure
 * mode to handle.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    public OrderResponse placeOrder(OrderRequest request) {
        ReservationResult result = inventoryService.reserve(request.productId(), request.quantity());

        String status = result.success() ? "CONFIRMED" : "REJECTED";

        Order order = new Order(request.productId(), request.quantity(), status, result.reason());
        Order saved = orderRepository.save(order);

        return new OrderResponse(saved.getOrderId(), status, result.reason(), result.inventory());
    }
}
