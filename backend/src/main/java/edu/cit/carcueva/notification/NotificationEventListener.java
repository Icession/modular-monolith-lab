package edu.cit.carcueva.notification;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import edu.cit.carcueva.inventory.LowStockEvent;
import edu.cit.carcueva.inventory.ReplenishmentReceivedEvent;
import edu.cit.carcueva.shop.OrderPlacedEvent;
import edu.cit.carcueva.shop.OrderRejectedEvent;

/**
 * The Notification module never calls OrderService or InventoryService;
 * it only listens to public event records. Nothing in shop, inventory or
 * supplier imports anything from this package.
 *
 * These listeners run synchronously (no @Async): writing one notification
 * row is cheap, and running inline means the notification exists by the
 * time the triggering request returns.
 */
@Component
class NotificationEventListener {

    private final NotificationRepository notificationRepository;

    NotificationEventListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    void onOrderPlaced(OrderPlacedEvent event) {
        notificationRepository.save(new Notification("Order " + event.orderId() + " confirmed"));
    }

    @EventListener
    void onOrderRejected(OrderRejectedEvent event) {
        notificationRepository.save(
                new Notification("Order " + event.orderId() + " rejected: " + event.reason()));
    }

    @EventListener
    void onLowStock(LowStockEvent event) {
        notificationRepository.save(new Notification(
                "Low stock: " + event.name() + " (" + event.productId() + ") has "
                        + event.remainingStock() + " left (threshold " + event.threshold() + "). Reorder requested."));
    }

    @EventListener
    void onReplenishmentReceived(ReplenishmentReceivedEvent event) {
        notificationRepository.save(new Notification(
                "Stock received: " + event.units() + " units of " + event.productId()
                        + " (reorder " + event.reference() + ")"));
    }
}
