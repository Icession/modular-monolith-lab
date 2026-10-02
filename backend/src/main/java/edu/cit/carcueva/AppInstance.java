package edu.cit.carcueva;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AppInstance {
    private static final Logger log = LoggerFactory.getLogger(AppInstance.class);

    private final String instanceId = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();

    public AppInstance() {
        log.info("[Instance] App instance ID: {} (started {})", instanceId, startedAt);
    }

    public String id() {
        return instanceId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public long uptimeSeconds() {
        return Duration.between(startedAt, Instant.now()).toSeconds();
    }
}
