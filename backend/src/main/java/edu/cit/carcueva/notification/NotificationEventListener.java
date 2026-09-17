package edu.cit.carcueva.notification;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import edu.cit.carcueva.inventory.LowStockEvent;
import edu.cit.carcueva.shop.OrderPlacedEvent;
import edu.cit.carcueva.shop.OrderRejectedEvent;

/**
 * The Notification module never calls OrderService or InventoryService,
 * and imports nothing from edu.cit.carcueva.shop or edu.cit.carcueva.inventory
 * other than their public event records (OrderPlacedEvent,
 * OrderRejectedEvent, LowStockEvent). Those events, and this listener,
 * are the ONLY coupling between Notification and the other two modules -
 * neither Order nor Inventory imports anything from this package at all,
 * so the dependency only ever points one way: Notification -> events.
 *
 * These listeners run synchronously (no @Async) on purpose for this lab:
 * writing a notification row is cheap, and running it inline means a
 * notification is guaranteed to exist by the time the HTTP response for
 * the triggering order/reservation returns, which makes the "Network tab
 * evidence" scenarios in the lab brief deterministic and easy to
 * reproduce. The trade-off is that a slow or failing notification write
 * would add latency to (or block) the order/reservation request itself,
 * since it runs on the same thread inside the same call stack. Making
 * these @Async would decouple that latency at the cost of losing the
 * synchronous guarantee - useful once Notification does something
 * heavier than a single insert (e.g. sending an email).
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
                "Reorder needed: " + event.name() + " (" + event.productId() + ") is low on stock - "
                        + event.remainingStock() + " remaining (threshold " + event.threshold() + ")"));
    }
}
