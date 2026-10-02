package edu.cit.carcueva.channel;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
class ChannelState {
    private final AtomicBoolean firstHeartbeatSent = new AtomicBoolean(false);
    private final AtomicBoolean listingsPublished = new AtomicBoolean(false);
    private final AtomicBoolean live = new AtomicBoolean(false);
    private final AtomicInteger stockHolds = new AtomicInteger(0);
    private final Object orderLock = new Object();
    private volatile Instant lastHeartbeatAt;
    private volatile Instant lastFeedReadAt;

    void heartbeatOk() {
        lastHeartbeatAt = Instant.now();
        firstHeartbeatSent.set(true);
    }

    void feedReadOk() {
        lastFeedReadAt = Instant.now();
    }

    void markListingsPublished() {
        listingsPublished.set(true);
    }

    void markLive() {
        live.set(true);
    }

    void holdStock() {
        stockHolds.incrementAndGet();
    }

    void releaseStock() {
        stockHolds.updateAndGet(n -> Math.max(n - 1, 0));
    }

    boolean stockHeld() {
        return stockHolds.get() > 0;
    }

    Object orderLock() {
        return orderLock;
    }

    boolean firstHeartbeatSent() {
        return firstHeartbeatSent.get();
    }

    boolean listingsPublished() {
        return listingsPublished.get();
    }

    boolean isLive() {
        return live.get();
    }

    Instant lastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    Instant lastFeedReadAt() {
        return lastFeedReadAt;
    }
}