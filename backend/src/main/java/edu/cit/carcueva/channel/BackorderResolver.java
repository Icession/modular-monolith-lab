package edu.cit.carcueva.channel;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import edu.cit.carcueva.inventory.ReplenishmentReceivedEvent;
import edu.cit.carcueva.shop.OrderAlreadyCancelledException;
import edu.cit.carcueva.shop.OrderResponse;
import edu.cit.carcueva.shop.OrderService;

@Component
class BackorderResolver {

    private static final Logger log = LoggerFactory.getLogger(BackorderResolver.class);

    private final AtomicBoolean deliveryArrived = new AtomicBoolean(false);
    private final ChannelOrderRepository repository;
    private final OrderService orderService;
    private final SupplyCheck supplyCheck;
    private final ChannelState state;
    private final TianggeReporter reporter;
    private final StockPublisher stockPublisher;
    private final Duration maxWait;
    private volatile Instant lastSweep = Instant.EPOCH;

    BackorderResolver(ChannelOrderRepository repository, OrderService orderService, SupplyCheck supplyCheck,
                      ChannelState state, TianggeReporter reporter, StockPublisher stockPublisher,
                      @Value("${channel.backorder-max-wait-minutes:30}") long maxWaitMinutes) {
        this.repository = repository;
        this.orderService = orderService;
        this.supplyCheck = supplyCheck;
        this.state = state;
        this.reporter = reporter;
        this.stockPublisher = stockPublisher;
        this.maxWait = Duration.ofMinutes(maxWaitMinutes);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReplenishmentReceived(ReplenishmentReceivedEvent event) {
        deliveryArrived.set(true);
        log.info("[Backorder] Supplier delivery arrived: {} units of {} - checking backorders",
                event.units(), event.productId());
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 15000)
    public void resolveIfDue() {
        if (!state.isLive()) {
            return;
        }
        boolean delivered = deliveryArrived.getAndSet(false);
        if (!delivered && Duration.between(lastSweep, Instant.now()).toSeconds() < 60) {
            return;
        }
        lastSweep = Instant.now();

        for (ChannelOrder candidate : repository.findByStateOrderByCreatedAtAsc(ChannelOrder.BACKORDERED)) {
            if (supplyCheck.shortfalls(FeedTranslator.decodeLines(candidate.getLines())).isEmpty()) {
                stockPublisher.flush("delivered stock, before filling backorder " + candidate.getTianggeOrderId(), true);
            }
            state.holdStock();
            try {
                resolveOne(candidate.getTianggeOrderId())
                        .ifPresent(report -> reporter.sendAll(List.of(report)));
            } catch (TianggeException e) {
                log.warn("[Backorder] Reporting failed for {}, will retry: {}",
                        candidate.getTianggeOrderId(), e.describe());
            } catch (RuntimeException e) {
                log.error("[Backorder] Could not resolve {}", candidate.getTianggeOrderId(), e);
            } finally {
                state.releaseStock();
                stockPublisher.flushAfterReports();
            }
        }
    }

    private Optional<TianggeReporter.Report> resolveOne(String tianggeOrderId) {
        synchronized (state.orderLock()) {
            ChannelOrder row = repository.findById(tianggeOrderId).orElse(null);
            if (row == null || !ChannelOrder.BACKORDERED.equals(row.getState()) || row.getShopOrderId() == null) {
                return Optional.empty();
            }
            OrderResponse local = orderService.fulfilBackorder(row.getShopOrderId());

            if ("CONFIRMED".equals(local.status())) {
                row.resolved(ChannelOrder.BACKORDER_FILLED, "ACCEPTED");
                log.info("[Backorder] {} filled from delivered stock -> ACCEPTED (SO-{})",
                        row.getTianggeOrderId(), row.getShopOrderId());
            } else if ("CANCELLED".equals(local.status())) {
                row.resolved(ChannelOrder.BACKORDER_CANCELLED, "CANCELLED");
                log.info("[Backorder] {} was cancelled locally -> CANCELLED", row.getTianggeOrderId());
            } else if (tooLong(row) || !stillComing(row)) {
                cancelLocal(row.getShopOrderId());
                row.resolved(ChannelOrder.BACKORDER_CANCELLED, "CANCELLED");
                log.info("[Backorder] {} cannot be filled (no restock on the way) -> CANCELLED",
                        row.getTianggeOrderId());
            } else {
                return Optional.empty();
            }
            repository.save(row);
            return Optional.of(new TianggeReporter.Report(row.getTianggeOrderId(), TianggeReporter.Kind.RESOLUTION));
        }
    }

    private boolean stillComing(ChannelOrder row) {
        Map<String, Integer> missing = supplyCheck.shortfalls(FeedTranslator.decodeLines(row.getLines()));
        return missing.isEmpty() || supplyCheck.restockOnItsWay(missing, true);
    }

    private boolean tooLong(ChannelOrder row) {
        return Duration.between(row.getCreatedAt(), Instant.now()).compareTo(maxWait) > 0;
    }

    private void cancelLocal(Long shopOrderId) {
        try {
            orderService.cancelOrder(shopOrderId);
        } catch (OrderAlreadyCancelledException e) {
            log.info("[Backorder] SO-{} already cancelled", shopOrderId);
        }
    }
}
