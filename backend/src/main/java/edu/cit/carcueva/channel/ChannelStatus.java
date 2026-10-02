package edu.cit.carcueva.channel;

import java.time.Instant;
import java.util.Map;

public record ChannelStatus(
        String instanceId,
        boolean live,
        Instant lastHeartbeatAt,
        Instant lastFeedReadAt,
        long cursor,
        Map<String, Long> ordersByState,
        long unreportedDecisions,
        long unconfirmedCancellations,
        long unreportedResolutions
) {
}
