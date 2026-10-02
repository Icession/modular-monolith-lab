package edu.cit.carcueva.channel;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import edu.cit.carcueva.inventory.InventoryService;
import edu.cit.carcueva.inventory.StockChangedEvent;
import jakarta.annotation.PreDestroy;

@Component
class StockPublisher {

    private static final Logger log = LoggerFactory.getLogger(StockPublisher.class);

    private static final Duration MAX_HOLD = Duration.ofSeconds(15);

    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private volatile Instant pendingSince;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stock-publisher");
        t.setDaemon(true);
        return t;
    });
    private final TianggeClient client;
    private final InventoryService inventoryService;
    private final ListedProducts listed;
    private final ChannelState state;

    StockPublisher(TianggeClient client, InventoryService inventoryService, ListedProducts listed, ChannelState state) {
        this.client = client;
        this.inventoryService = inventoryService;
        this.listed = listed;
        this.state = state;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStockChanged(StockChangedEvent event) {
        if (!listed.contains(event.productId())) {
            return;
        }
        markPending(List.of(event.productId()));
        if (!state.stockHeld()) {
            sender.submit(() -> flush("stock changed: " + event.productId(), false));
        } else if (overdue()) {
            sender.submit(() -> flush("stock waited too long", true));
        }
    }

    void publishAll() {
        markPending(listed.all());
        flush("go-live", false);
    }

    void flushAfterReports() {
        if (!pending.isEmpty()) {
            sender.submit(() -> flush("after reporting to Tiangge", false));
        }
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 20000)
    public void retryFailedPublishes() {
        if (pending.isEmpty()) {
            return;
        }
        if (!state.stockHeld()) {
            sender.submit(() -> flush("retry", false));
        } else if (overdue()) {
            sender.submit(() -> flush("stock waited too long", true));
        }
    }

    private void markPending(java.util.Collection<String> productIds) {
        if (pendingSince == null) {
            pendingSince = Instant.now();
        }
        pending.addAll(productIds);
    }

    private boolean overdue() {
        Instant since = pendingSince;
        return since != null && Duration.between(since, Instant.now()).compareTo(MAX_HOLD) > 0;
    }

    synchronized void flush(String why, boolean force) {
        if (!state.listingsPublished() || pending.isEmpty() || (state.stockHeld() && !force)) {
            return;
        }
        Instant since = pendingSince;
        pendingSince = null;
        List<String> batch = new ArrayList<>(pending);
        pending.removeAll(batch);

        Map<String, Integer> available = new LinkedHashMap<>();
        for (String productId : batch) {
            inventoryService.getItem(productId).ifPresent(item -> available.put(productId, item.stock()));
        }
        if (available.isEmpty()) {
            return;
        }
        if (!pending.isEmpty() && pendingSince == null) {
            pendingSince = Instant.now();
        }

        try {
            client.publishStock(available);
            log.info("[Stock] Published {} ({})", available, why);
        } catch (TianggeException e) {
            pending.addAll(batch);
            if (pendingSince == null || (since != null && since.isBefore(pendingSince))) {
                pendingSince = since == null ? Instant.now() : since;
            }
            log.warn("[Stock] Could not publish {} - will retry: {}", available, e.describe());
        }
    }

    @PreDestroy
    void shutdown() {
        sender.shutdownNow();
    }
}
