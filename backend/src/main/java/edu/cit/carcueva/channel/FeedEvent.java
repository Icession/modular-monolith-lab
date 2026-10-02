package edu.cit.carcueva.channel;

import java.time.Instant;
import java.util.List;

record FeedEvent(
        long seq,
        String eventId,
        String type,
        String orderId,
        Instant placedAt,
        Instant decisionDeadline,
        Instant cancelledAt,
        List<FeedLine> lines
) {
    static final String ORDER_PLACED = "ORDER_PLACED";
    static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    record FeedLine(String sellerSku, int qty) {
    }
}
