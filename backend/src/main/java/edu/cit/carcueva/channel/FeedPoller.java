package edu.cit.carcueva.channel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

@Component
class FeedPoller {

    private static final Logger log = LoggerFactory.getLogger(FeedPoller.class);

    private final AtomicInteger threadCount = new AtomicInteger();
    private final ExecutorService deciders;

    private final TianggeClient client;
    private final FeedCursorStore cursorStore;
    private final ChannelState state;
    private final OrderDecider decider;
    private final CancellationHandler cancellations;
    private final StockPublisher stockPublisher;
    private final TianggeReporter reporter;
    private final int pageSize;

    FeedPoller(TianggeClient client, FeedCursorStore cursorStore, ChannelState state, OrderDecider decider,
               CancellationHandler cancellations, StockPublisher stockPublisher, TianggeReporter reporter,
               @Value("${channel.feed-page-size:50}") int pageSize,
               @Value("${channel.parallel-decisions:6}") int parallelDecisions) {
        this.client = client;
        this.cursorStore = cursorStore;
        this.state = state;
        this.decider = decider;
        this.cancellations = cancellations;
        this.stockPublisher = stockPublisher;
        this.reporter = reporter;
        this.pageSize = pageSize;
        this.deciders = Executors.newFixedThreadPool(parallelDecisions, r -> {
            Thread t = new Thread(r, "order-decider-" + threadCount.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Scheduled(fixedDelayString = "${channel.feed-interval-ms:3000}", initialDelay = 5000)
    public void poll() {
        if (!state.isLive()) {
            return;
        }
        step("read feed", this::readFeed);
        step("resume undecided orders", () -> withStockHeld(() -> reporter.sendAll(decider.resumeUndecided())));
        step("retry unreported updates", () -> withStockHeld(reporter::retryUnreported));
    }

    private void readFeed() {
        for (int page = 0; page < 5; page++) {
            boolean more = withStockHeldResult(this::readOnePage);
            if (!more) {
                return;
            }
        }
    }

    private boolean readOnePage() {
        long cursor = cursorStore.load();
        JsonNode body = client.readFeed(cursor, pageSize);
        state.feedReadOk();

        List<FeedEvent> events = FeedTranslator.events(body);
        List<TianggeReporter.Report> reports = new ArrayList<>();
        long firstFailedSeq = Long.MAX_VALUE;

        Map<String, FeedEvent> placements = new LinkedHashMap<>();
        for (FeedEvent event : events) {
            if (FeedEvent.ORDER_PLACED.equals(event.type()) && event.orderId() != null) {
                placements.putIfAbsent(event.orderId(), event);
            }
        }

        Map<FeedEvent, Future<Optional<TianggeReporter.Report>>> running = new LinkedHashMap<>();
        for (FeedEvent event : placements.values()) {
            running.put(event, deciders.submit(() -> decideAndReport(event)));
        }
        for (Map.Entry<FeedEvent, Future<Optional<TianggeReporter.Report>>> entry : running.entrySet()) {
            try {
                entry.getValue().get(50, TimeUnit.SECONDS);
            } catch (Exception e) {
                FeedEvent event = entry.getKey();
                log.error("[Feed] Could not decide {} (seq {}) - will retry", event.orderId(), event.seq(), e);
                firstFailedSeq = Math.min(firstFailedSeq, event.seq());
            }
        }

        for (FeedEvent event : events) {
            if (event.seq() >= firstFailedSeq) {
                break;
            }
            if (FeedEvent.ORDER_PLACED.equals(event.type())) {
                continue;
            }
            try {
                if (FeedEvent.ORDER_CANCELLED.equals(event.type())) {
                    cancellations.onOrderCancelled(event).ifPresent(reports::add);
                } else {
                    log.info("[Feed] Ignoring unknown event type {} (seq {})", event.type(), event.seq());
                }
            } catch (RuntimeException e) {
                log.error("[Feed] Could not handle {} {} (seq {}) - will retry",
                        event.type(), event.orderId(), event.seq(), e);
                firstFailedSeq = Math.min(firstFailedSeq, event.seq());
                break;
            }
        }

        boolean stoppedEarly = firstFailedSeq != Long.MAX_VALUE;
        long handledUpTo = cursor;
        for (FeedEvent event : events) {
            if (event.seq() < firstFailedSeq) {
                handledUpTo = Math.max(handledUpTo, event.seq());
            }
        }
        OptionalLong next = FeedTranslator.nextCursor(body);
        long newCursor = stoppedEarly || next.isEmpty() ? handledUpTo : Math.max(handledUpTo, next.getAsLong());
        if (newCursor > cursor) {
            cursorStore.save(newCursor);
            log.info("[Feed] {} event(s), cursor {} -> {}", events.size(), cursor, newCursor);
        }

        reporter.sendAll(reports);
        return !stoppedEarly && events.size() >= pageSize;
    }

    private Optional<TianggeReporter.Report> decideAndReport(FeedEvent event) {
        state.holdStock();
        Optional<TianggeReporter.Report> report;
        try {
            report = decider.onOrderPlaced(event);
        } catch (RuntimeException e) {
            releaseAndFlush();
            throw e;
        }
        if (report.isPresent()) {
            reporter.sendAsync(report.get(), this::releaseAndFlush);
        } else {
            releaseAndFlush();
        }
        return report;
    }

    private void releaseAndFlush() {
        state.releaseStock();
        stockPublisher.flushAfterReports();
    }

    private void withStockHeld(Runnable action) {
        withStockHeldResult(() -> {
            action.run();
            return true;
        });
    }

    private boolean withStockHeldResult(java.util.function.BooleanSupplier action) {
        state.holdStock();
        try {
            return action.getAsBoolean();
        } finally {
            state.releaseStock();
            stockPublisher.flushAfterReports();
        }
    }

    private void step(String name, Runnable action) {
        try {
            action.run();
        } catch (TianggeException e) {
            log.warn("[Feed] {} failed: {}", name, e.describe());
        } catch (RuntimeException e) {
            log.error("[Feed] {} failed", name, e);
        }
    }

    @PreDestroy
    void shutdown() {
        deciders.shutdownNow();
    }
}
