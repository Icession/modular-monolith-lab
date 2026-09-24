package edu.cit.carcueva.supplier;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.carcueva.inventory.ReplenishmentReceivedEvent;

@Component
class SupplierOrderStore {

    static final Set<SupplierOrderStatus> OPEN = EnumSet.of(
            SupplierOrderStatus.PENDING, SupplierOrderStatus.SUBMITTED, SupplierOrderStatus.IN_PROGRESS,
            SupplierOrderStatus.SHIPPED, SupplierOrderStatus.NEEDS_ATTENTION);

    static final Set<SupplierOrderStatus> TRACKED = EnumSet.of(
            SupplierOrderStatus.SUBMITTED, SupplierOrderStatus.IN_PROGRESS,
            SupplierOrderStatus.SHIPPED, SupplierOrderStatus.NEEDS_ATTENTION);

    private final SupplierOrderRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    SupplierOrderStore(SupplierOrderRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SupplierOrder createPending(String productId, int cases, int units) {
        SupplierOrder order = repository.save(
                new SupplierOrder(productId, UUID.randomUUID().toString(), cases, units));
        order.assignBuyerRef("RO-" + order.getId() + "-" + order.getRequestId().substring(0, 6));
        return order;
    }

    @Transactional
    public void recordAttempt(Long id) {
        repository.findById(id).ifPresent(SupplierOrder::recordAttempt);
    }

    @Transactional
    public void markSubmitted(Long id, String poNumber, SupplierOrderStatus status, String note) {
        repository.findById(id).ifPresent(o -> o.markSubmitted(poNumber, status, note));
    }

    /** Still PENDING - the scheduled dispatcher will try again later. */
    @Transactional
    public void recordRetryableFailure(Long id, String error) {
        repository.findById(id).ifPresent(o -> o.recordError(error));
    }

    @Transactional
    public void markFailed(Long id, String error) {
        repository.findById(id).ifPresent(o -> o.changeStatus(SupplierOrderStatus.FAILED, error));
    }

    @Transactional
    public void markNeedsAttention(Long id, String note) {
        repository.findById(id).ifPresent(o -> o.changeStatus(SupplierOrderStatus.NEEDS_ATTENTION, note));
    }

    @Transactional
    public void markCancelled(Long id, String note) {
        repository.findById(id).ifPresent(o -> o.changeStatus(SupplierOrderStatus.CANCELLED, note));
    }

    @Transactional
    public void updateTrackedStatus(Long id, SupplierOrderStatus status) {
        repository.findById(id).ifPresent(o -> {
            if (o.getStatus() != status) {
                o.changeStatus(status, null);
            }
        });
    }

    @Transactional
    public void markDelivered(Long id) {
        repository.findById(id).ifPresent(o -> {
            if (o.getStatus() == SupplierOrderStatus.DELIVERED) {
                return;
            }
            o.changeStatus(SupplierOrderStatus.DELIVERED, null);
            eventPublisher.publishEvent(new ReplenishmentReceivedEvent(o.getProductId(), o.getUnits(), o.getBuyerRef()));
        });
    }

    @Transactional(readOnly = true)
    public Optional<SupplierOrder> find(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<SupplierOrder> findOpenOrderFor(String productId) {
        return repository.findFirstByProductIdAndStatusInOrderByCreatedAtDesc(productId, OPEN);
    }

    @Transactional(readOnly = true)
    public List<SupplierOrder> pendingReadyToSend(Instant untouchedSince) {
        return repository.findTop5ByStatusAndUpdatedAtBeforeOrderByCreatedAtAsc(
                SupplierOrderStatus.PENDING, untouchedSince);
    }

    @Transactional(readOnly = true)
    public List<SupplierOrder> ordersToTrack() {
        return repository.findTop10ByStatusInAndPoNumberIsNotNullOrderByUpdatedAtAsc(TRACKED);
    }

    @Transactional(readOnly = true)
    public List<SupplierOrder> all() {
        return repository.findAllByOrderByCreatedAtDesc();
    }
}
