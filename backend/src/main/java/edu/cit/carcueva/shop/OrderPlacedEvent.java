package edu.cit.carcueva.shop;

/**
 * Published by OrderService after an order is successfully CONFIRMED.
 *
 * Public on purpose: this (and OrderRejectedEvent) is the entire
 * contract the Notification module is allowed to depend on. Order
 * never imports anything from the notification package, and this
 * event carries no reference to OrderService or any Order entity -
 * just plain data.
 */
public record OrderPlacedEvent(Long orderId, String status, String message) {
}
