package edu.cit.carcueva.shop;

/**
 * Published by OrderService after an order is REJECTED, whether from
 * pre-reservation stock validation or a reservation race condition
 * caught during the transactional reserve step.
 */
public record OrderRejectedEvent(Long orderId, String reason) {
}
