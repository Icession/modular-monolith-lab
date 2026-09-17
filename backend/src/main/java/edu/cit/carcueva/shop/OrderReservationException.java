package edu.cit.carcueva.shop;

/**
 * Internal to OrderService: thrown when a reservation fails AFTER
 * pre-validation already said it should succeed (a race condition -
 * another order consumed the remaining stock in between). Package-
 * private and unchecked so it triggers @Transactional rollback of
 * every reserve() call made earlier in the same multi-item order,
 * undoing them atomically within the shared database transaction.
 */
class OrderReservationException extends RuntimeException {

    private final String failedProductId;

    OrderReservationException(String failedProductId, String message) {
        super(message);
        this.failedProductId = failedProductId;
    }

    String failedProductId() {
        return failedProductId;
    }
}
