package edu.cit.carcueva.inventory;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryServiceImpl implements InventoryService {
    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${inventory.low-stock-threshold:5}") int lowStockThreshold) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    public Optional<InventoryItemView> getItem(String productId) {
        return inventoryRepository.findById(productId).map(this::toView);
    }

    @Override
    public List<InventoryItemView> getAllItems() {
        return inventoryRepository.findAll().stream().map(this::toView).toList();
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        Optional<InventoryItem> maybeItem = inventoryRepository.findForUpdate(productId);

        if (maybeItem.isEmpty()) {
            return ReservationResult.rejected("Product " + productId + " does not exist", null);
        }

        InventoryItem item = maybeItem.get();

        if (quantity <= 0) {
            return ReservationResult.rejected("Quantity must be greater than zero", toView(item));
        }

        if (quantity > item.getStock()) {
            return ReservationResult.rejected(
                    "Requested quantity (" + quantity + ") exceeds available stock (" + item.getStock() + ")",
                    toView(item));
        }

        item.setStock(item.getStock() - quantity);
        InventoryItem saved = inventoryRepository.save(item);

        eventPublisher.publishEvent(new StockChangedEvent(saved.getProductId(), saved.getStock()));

        if (saved.getStock() < lowStockThreshold) {
            eventPublisher.publishEvent(
                    new LowStockEvent(saved.getProductId(), saved.getName(), saved.getStock(), lowStockThreshold));
        }

        return ReservationResult.approved(toView(saved));
    }

    @Override
    @Transactional
    public ReservationResult restock(String productId, int quantity) {
        Optional<InventoryItem> maybeItem = inventoryRepository.findForUpdate(productId);

        if (maybeItem.isEmpty()) {
            return ReservationResult.rejected("Product " + productId + " does not exist", null);
        }

        InventoryItem item = maybeItem.get();
        item.setStock(item.getStock() + quantity);
        InventoryItem saved = inventoryRepository.save(item);

        eventPublisher.publishEvent(new StockChangedEvent(saved.getProductId(), saved.getStock()));

        return ReservationResult.approved(toView(saved));
    }

    private InventoryItemView toView(InventoryItem item) {
        return new InventoryItemView(item.getProductId(), item.getName(), item.getStock());
    }
}
