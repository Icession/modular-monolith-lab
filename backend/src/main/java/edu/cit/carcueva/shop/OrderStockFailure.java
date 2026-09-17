package edu.cit.carcueva.shop;

/**
 * One line item's stock-validation failure, found during the
 * pre-reservation check in OrderService.validateStock().
 */
record OrderStockFailure(String productId, String reason) {
}
