package edu.cit.carcueva.inventory;

public record StockChangedEvent(String productId, int available) {
}
