package edu.cit.carcueva.supplier;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    /** PENDING reorders nobody has touched for a while - ready to (re)send. */
    List<SupplierOrder> findTop5ByStatusAndUpdatedAtBeforeOrderByCreatedAtAsc(
            SupplierOrderStatus status, Instant untouchedSince);

    /** Orders the supplier knows about that haven't finished yet - to poll for status. */
    List<SupplierOrder> findTop10ByStatusInAndPoNumberIsNotNullOrderByUpdatedAtAsc(
            Collection<SupplierOrderStatus> statuses);

    Optional<SupplierOrder> findFirstByProductIdAndStatusInOrderByCreatedAtDesc(
            String productId, Collection<SupplierOrderStatus> statuses);

    List<SupplierOrder> findAllByOrderByCreatedAtDesc();
}
