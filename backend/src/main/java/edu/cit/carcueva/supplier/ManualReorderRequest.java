package edu.cit.carcueva.supplier;

/** Body for POST /api/supplier/reorders - a manual test trigger, in our own terms. */
public record ManualReorderRequest(String productId, int units) {
}
