package edu.cit.carcueva.channel;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import edu.cit.carcueva.shop.OrderAlreadyCancelledException;
import edu.cit.carcueva.shop.OrderNotFoundException;
import edu.cit.carcueva.shop.OrderResponse;
import edu.cit.carcueva.shop.OrderService;

@Component
class CancellationHandler {
    private static final Logger log = LoggerFactory.getLogger(CancellationHandler.class);

    private final ChannelOrderRepository repository;
    private final OrderService orderService;
    private final ChannelState state;

    CancellationHandler(ChannelOrderRepository repository, OrderService orderService, ChannelState state) {
        this.repository = repository;
        this.orderService = orderService;
        this.state = state;
    }

    Optional<TianggeReporter.Report> onOrderCancelled(FeedEvent event) {
        if (event.orderId() == null) {
            return Optional.empty();
        }
        synchronized (state.orderLock()) {
            return handle(event);
        }
    }

    private Optional<TianggeReporter.Report> handle(FeedEvent event) {
        ChannelOrder row = repository.findById(event.orderId())
                .orElseGet(() -> new ChannelOrder(event.orderId(), "", null, null));

        if (row.getCancelRequestedAt() != null) {
            log.info("[Feed] Cancellation of {} delivered again (seq {}) - already handled, skipping",
                    event.orderId(), event.seq());
            return Optional.empty();
        }

        boolean restocked = false;
        if (row.getShopOrderId() != null) {
            try {
                OrderResponse local = orderService.getOrder(row.getShopOrderId());
                if ("CONFIRMED".equals(local.status()) || "BACKORDERED".equals(local.status())) {
                    orderService.cancelOrder(row.getShopOrderId());
                    restocked = "CONFIRMED".equals(local.status());
                }
            } catch (OrderAlreadyCancelledException | OrderNotFoundException e) {
                log.info("[Cancel] Local order for {} already cancelled or missing: {}", event.orderId(), e.getMessage());
            }
        }

        row.cancelledByCustomer(restocked);
        repository.save(row);
        log.info("[Cancel] {} cancelled by customer -> local SO-{} cancelled, restocked={}",
                event.orderId(), row.getShopOrderId(), restocked);
        return Optional.of(new TianggeReporter.Report(row.getTianggeOrderId(), TianggeReporter.Kind.CANCELLATION));
    }
}