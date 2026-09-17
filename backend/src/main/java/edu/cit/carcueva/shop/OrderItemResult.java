package edu.cit.carcueva.shop;

/**
 * Per-line-item outcome in an OrderResponse. Because orders are
 * all-or-nothing, every item in one order shares the same outcome
 * (CONFIRMED or REJECTED) - but `reason` is only populated on the
 * specific item(s) that actually caused a rejection, so the caller
 * can tell which product was the problem.
 */
public record OrderItemResult(String productId, int quantity, String outcome, String reason) {
}
