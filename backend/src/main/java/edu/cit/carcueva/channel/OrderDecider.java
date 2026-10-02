package edu.cit.carcueva.channel;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import edu.cit.carcueva.shop.OrderItemRequest;
import edu.cit.carcueva.shop.OrderRequest;
import edu.cit.carcueva.shop.OrderResponse;
import edu.cit.carcueva.shop.OrderService;

@Component
class OrderDecider {
    private static final Logger log = LoggerFactory.getLogger(OrderDecider.class);

    private final ChannelOrderRepository repository;
    private final OrderService orderService;
    private final SupplyCheck supplyCheck;

    OrderDecider(ChannelOrderRepository repository, OrderService orderService, SupplyCheck supplyCheck) {
        this.repository = repository;
        this.orderService = orderService;
        this.supplyCheck = supplyCheck;
    }

    Optional<TianggeReporter.Report> onOrderPlaced(FeedEvent event) {
        if (event.orderId() == null) {
            return Optional.empty();
        }
        Optional<ChannelOrder> existing = repository.findById(event.orderId());
        if (existing.isPresent()) {
            if (ChannelOrder.NEW.equals(existing.get().getState())) {
                return Optional.of(decide(existing.get()));
            }
            log.info("[Feed] {} delivered again (seq {}, event {}) - already handled as {}, skipping",
                    event.orderId(), event.seq(), event.eventId(), existing.get().getState());
            return Optional.empty();
        }

        Map<String, Integer> quantities = FeedTranslator.quantities(event.lines());
        ChannelOrder row = repository.save(new ChannelOrder(event.orderId(),
                FeedTranslator.encodeLines(quantities), event.placedAt(), event.decisionDeadline()));
        return Optional.of(decide(row));
    }

    List<TianggeReporter.Report> resumeUndecided() {
        List<TianggeReporter.Report> reports = new ArrayList<>();
        for (ChannelOrder row : repository.findTop20ByStateAndCreatedAtBeforeOrderByCreatedAtAsc(
                ChannelOrder.NEW, Instant.now().minusSeconds(10))) {
            log.info("[Feed] Resuming undecided order {}", row.getTianggeOrderId());
            reports.add(decide(row));
        }
        return reports;
    }

    private TianggeReporter.Report decide(ChannelOrder row) {
        Instant started = Instant.now();
        Map<String, Integer> quantities = FeedTranslator.decodeLines(row.getLines());

        if (quantities.isEmpty()) {
            row.decided(null, ChannelOrder.REJECTED, "REJECTED", "Order has no valid lines");
            repository.save(row);
            return new TianggeReporter.Report(row.getTianggeOrderId(), TianggeReporter.Kind.DECISION);
        }

        OrderRequest request = new OrderRequest(quantities.entrySet().stream()
                .map(e -> new OrderItemRequest(e.getKey(), e.getValue()))
                .toList());

        OrderResponse local;
        Optional<Map<String, Integer>> shortfalls = supplyCheck.shortfallsIfAllKnown(quantities);
        boolean known = shortfalls.isPresent();
        Map<String, Integer> missing = shortfalls.orElse(Map.of());

        if (known && !missing.isEmpty() && supplyCheck.restockOnItsWay(missing, hasTimeForSupplier(row))) {
            local = orderService.placeBackorder(request, "Waiting for supplier delivery of " + missing);
        } else {
            local = orderService.placeOrder(request);
        }

        String decision = switch (local.status()) {
            case "CONFIRMED" -> "ACCEPTED";
            case "BACKORDERED" -> "BACKORDERED";
            default -> "REJECTED";
        };
        String state = switch (decision) {
            case "ACCEPTED" -> ChannelOrder.ACCEPTED;
            case "BACKORDERED" -> ChannelOrder.BACKORDERED;
            default -> ChannelOrder.REJECTED;
        };
        String reason = "ACCEPTED".equals(decision) ? null : local.reason();

        row.decided(local.orderId(), state, decision, reason);
        repository.save(row);

        log.info("[Order] {} {} -> {} as SO-{} in {} ms{}",
                row.getTianggeOrderId(), quantities, decision, local.orderId(),
                Duration.between(started, Instant.now()).toMillis(),
                reason == null ? "" : " (" + reason + ")");
        return new TianggeReporter.Report(row.getTianggeOrderId(), TianggeReporter.Kind.DECISION);
    }

    private static boolean hasTimeForSupplier(ChannelOrder row) {
        return row.getDecisionDeadline() == null
                || Instant.now().plusSeconds(15).isBefore(row.getDecisionDeadline());
    }
}
