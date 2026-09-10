package edu.cit.carcueva.inventory;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private on purpose: this class is invisible outside
 * edu.cit.carcueva.inventory. Spring can still discover and wire it
 * as the InventoryService bean via component scanning + reflection,
 * but no code in the shop (Order) module can import, reference, or
 * instantiate InventoryServiceImpl directly — the compiler enforces
 * that Order can only ever hold a reference typed as the public
 * InventoryService interface.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public Optional<InventoryItemView> getItem(String productId) {
        return inventoryRepository.findById(productId).map(this::toView);
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        Optional<InventoryItem> maybeItem = inventoryRepository.findById(productId);

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
        return ReservationResult.approved(toView(saved));
    }

    private InventoryItemView toView(InventoryItem item) {
        return new InventoryItemView(item.getProductId(), item.getName(), item.getStock());
    }
}
