package edu.cit.carcueva.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Package-private: only classes inside the inventory module may touch
 * the repository directly. The Order module never sees this.
 */
interface InventoryRepository extends JpaRepository<InventoryItem, String> {
}
