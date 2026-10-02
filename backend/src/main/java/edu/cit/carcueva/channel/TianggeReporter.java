package edu.cit.carcueva.channel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
class TianggeReporter {
    private static final Logger log = LoggerFactory.getLogger(TianggeReporter.class);

    enum Kind { DECISION, RESOLUTION, CANCELLATION }

    record Report(String orderId, Kind kind) {
    }

    private enum Outcome { DONE, DONE_WITH_ERROR, RETRY }

    private record Result(Outcome outcome, String detail) {
    }

    private record Call(String orderId, Kind kind, String decision, String shopOrderRef, String reason,
                        String resolution, boolean restocked, Instant deadline) {
    }

    private final AtomicInteger threadCount = new AtomicInteger();
    private final ExecutorService pool = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "tiangge-report-" + threadCount.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final TianggeClient client;
    private final ChannelOrderRepository repository;
    private volatile Instant retryPausedUntil = Instant.EPOCH;

    TianggeReporter(TianggeClient client, ChannelOrderRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    void sendAsync(Report report, Runnable after) {
        String key = key(report);
        if (!inFlight.add(key)) {
            after.run();
            return;
        }
        try {
            pool.submit(() -> {
                try {
                    repository.findById(report.orderId())
                            .map(row -> send(toCall(row, report.kind())))
                            .ifPresent(result -> apply(report, result));
                } catch (RuntimeException e) {
                    log.error("[Report] {} for {} failed, will retry", report.kind(), report.orderId(), e);
                } finally {
                    inFlight.remove(key);
                    after.run();
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.remove(key);
            after.run();
        }
    }

    void sendAll(List<Report> reports) {
        if (reports.isEmpty()) {
            return;
        }
        Map<Report, Future<Result>> futures = new LinkedHashMap<>();
        for (Report report : reports) {
            String key = key(report);
            if (!inFlight.add(key)) {
                continue;
            }
            Optional<Call> call = repository.findById(report.orderId()).map(row -> toCall(row, report.kind()));
            if (call.isEmpty()) {
                inFlight.remove(key);
                continue;
            }
            futures.put(report, pool.submit(() -> send(call.get())));
        }
        futures.forEach((report, future) -> {
            try {
                Result result;
                try {
                    result = future.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    result = new Result(Outcome.RETRY, "Timed out waiting for report: " + e.getMessage());
                }
                apply(report, result);
            } finally {
                inFlight.remove(key(report));
            }
        });
    }

    private static String key(Report report) {
        return report.orderId() + ":" + report.kind();
    }

    void retryUnreported() {
        if (Instant.now().isBefore(retryPausedUntil)) {
            return;
        }
        List<Report> reports = new ArrayList<>();
        repository.findTop20ByDecisionIsNotNullAndDecisionReportedAtIsNullOrderByCreatedAtAsc()
                .forEach(r -> reports.add(new Report(r.getTianggeOrderId(), Kind.DECISION)));
        repository.findTop20ByCancelRequestedAtIsNotNullAndCancelConfirmedAtIsNullOrderByCreatedAtAsc()
                .forEach(r -> reports.add(new Report(r.getTianggeOrderId(), Kind.CANCELLATION)));
        repository.findTop20ByResolutionIsNotNullAndResolutionReportedAtIsNullOrderByCreatedAtAsc()
                .forEach(r -> reports.add(new Report(r.getTianggeOrderId(), Kind.RESOLUTION)));
        if (!reports.isEmpty()) {
            log.info("[Report] Retrying {} unreported update(s)", reports.size());
            sendAll(reports);
        }
    }

    private Call toCall(ChannelOrder row, Kind kind) {
        return new Call(row.getTianggeOrderId(), kind, row.getDecision(), row.shopOrderRef(),
                row.getDecisionReason(), row.getResolution(), Boolean.TRUE.equals(row.getCancelRestocked()),
                row.getDecisionDeadline());
    }

    private Result send(Call call) {
        try {
            switch (call.kind()) {
                case DECISION -> client.sendDecision(call.orderId(), call.decision(),
                        call.shopOrderRef() == null ? "SO-NONE" : call.shopOrderRef(), call.reason());
                case RESOLUTION -> client.sendResolution(call.orderId(), call.resolution());
                case CANCELLATION -> client.confirmCancellation(call.orderId(), call.restocked());
            }
            return new Result(Outcome.DONE, describe(call));
        } catch (TianggeException e) {
            if (e.kind() == TianggeException.Kind.TRANSIENT || e.kind() == TianggeException.Kind.AUTH) {
                return new Result(Outcome.RETRY, e.describe());
            }
            return new Result(Outcome.DONE_WITH_ERROR, e.describe());
        } catch (RuntimeException e) {
            return new Result(Outcome.RETRY, e.toString());
        }
    }

    private void apply(Report report, Result result) {
        repository.findById(report.orderId()).ifPresent(row -> {
            if (result.outcome() == Outcome.RETRY) {
                row.recordError(report.kind() + ": " + result.detail());
                repository.save(row);
                retryPausedUntil = Instant.now().plusSeconds(5);
                log.warn("[Report] {} for {} not delivered yet, will retry: {}",
                        report.kind(), report.orderId(), result.detail());
                return;
            }
            switch (report.kind()) {
                case DECISION -> row.decisionReported();
                case RESOLUTION -> row.resolutionReported();
                case CANCELLATION -> row.cancellationConfirmed();
            }
            if (result.outcome() == Outcome.DONE_WITH_ERROR) {
                row.recordError(report.kind() + ": " + result.detail());
                log.warn("[Report] {} for {} refused by Tiangge (not retrying): {}",
                        report.kind(), report.orderId(), result.detail());
            } else {
                log.info("[Report] {}", result.detail());
            }
            repository.save(row);
        });
    }

    private static String describe(Call call) {
        return switch (call.kind()) {
            case DECISION -> {
                boolean late = call.deadline() != null && Instant.now().isAfter(call.deadline());
                yield "Decision " + call.decision() + " (" + call.shopOrderRef() + ") sent for " + call.orderId()
                        + (late ? " - AFTER the deadline" : " - on time");
            }
            case RESOLUTION -> "Backorder " + call.orderId() + " resolved as " + call.resolution();
            case CANCELLATION -> "Cancellation of " + call.orderId() + " confirmed (restocked=" + call.restocked() + ")";
        };
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
