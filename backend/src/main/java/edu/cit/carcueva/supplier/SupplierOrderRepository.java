package edu.cit.carcueva.supplier;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {
    List<SupplierOrder> findTop5ByStatusAndUpdatedAtBeforeOrderByCreatedAtAsc(
            SupplierOrderStatus status, Instant untouchedSince);

    List<SupplierOrder> findTop10ByStatusInAndPoNumberIsNotNullOrderByUpdatedAtAsc(
            Collection<SupplierOrderStatus> statuses);

    Optional<SupplierOrder> findFirstByProductIdAndStatusInOrderByCreatedAtDesc(
            String productId, Collection<SupplierOrderStatus> statuses);

    List<SupplierOrder> findAllByOrderByCreatedAtDesc();

    boolean existsByProductIdAndStatusInAndPoNumberIsNotNull(
            String productId, Collection<SupplierOrderStatus> statuses);
}
