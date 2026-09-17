package edu.cit.carcueva.shop;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super("Order " + orderId + " does not exist");
    }
}
