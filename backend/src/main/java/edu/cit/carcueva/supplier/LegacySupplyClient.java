package edu.cit.carcueva.supplier;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * The three LegacySupply operations we use, each wrapped in the resilience
 * rules. Package-private.
 *
 * Per call:  at most `maxAttempts` tries (default 3), exponential backoff
 *            with jitter between transient failures (~0.5s, ~1s), immediate
 *            re-sign-in when the session is rejected.
 * Across calls: a short local cool-down after an outage or a quota refusal,
 *            so the scheduled jobs don't burn our request quota knocking on
 *            a door that's obviously closed.
 */
@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);

    private final LegacySupplyHttp http;
    private final LegacySupplySession session;
    private final int maxAttempts;
    private final long baseBackoffMs;

    private volatile Instant pausedUntil = Instant.EPOCH;

    LegacySupplyClient(
            LegacySupplyHttp http,
            LegacySupplySession session,
            @Value("${legacysupply.max-attempts:3}") int maxAttempts,
            @Value("${legacysupply.backoff-ms:500}") long baseBackoffMs) {
        this.http = http;
        this.session = session;
        this.maxAttempts = maxAttempts;
        this.baseBackoffMs = baseBackoffMs;
    }

    boolean isCoolingDown() {
        return Instant.now().isBefore(pausedUntil);
    }

    /** POST /purchase-orders. The same requestId on every try = LegacySupply processes it at most once. */
    PurchaseOrderAck placeOrder(String supplierSku, int qty, String buyerRef, String requestId) {
        String xml = XmlCodec.purchaseOrder(supplierSku, qty, buyerRef);
        return withRetry("place " + buyerRef, token -> {
            String body = http.send(HttpMethod.POST, "/purchase-orders", xml,
                    Map.of("X-LS-Session", token, "X-Request-Id", requestId));
            return XmlCodec.readAck(XmlCodec.parse(body).getDocumentElement());
        });
    }

    /** GET /purchase-orders/{PoNumber} */
    PurchaseOrderAck getOrder(String poNumber) {
        return withRetry("track " + poNumber, token -> {
            String body = http.send(HttpMethod.GET, "/purchase-orders/" + poNumber, null,
                    Map.of("X-LS-Session", token));
            return XmlCodec.readAck(XmlCodec.parse(body).getDocumentElement());
        });
    }

    /** GET /purchase-orders?buyerRef=... - "did an earlier attempt already create this order?" */
    Optional<PurchaseOrderAck> findByBuyerRef(String buyerRef) {
        return withRetry("lookup " + buyerRef, token -> {
            String body = http.send(HttpMethod.GET, "/purchase-orders?buyerRef=" + buyerRef, null,
                    Map.of("X-LS-Session", token));
            return XmlCodec.readAcks(XmlCodec.parse(body)).stream()
                    .filter(ack -> ack.buyerRef() == null || buyerRef.equals(ack.buyerRef()))
                    .findFirst();
        });
    }

    private <T> T withRetry(String operation, Function<String, T> call) {
        if (isCoolingDown()) {
            throw LegacySupplyException.coolingDown(Duration.between(Instant.now(), pausedUntil));
        }

        LegacySupplyException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String token = null;
            try {
                token = session.currentToken();
                T result = call.apply(token);
                if (attempt > 1) {
                    log.info("[LegacySupply] {} succeeded on attempt {}/{}", operation, attempt, maxAttempts);
                }
                return result;
            } catch (LegacySupplyException e) {
                last = e;
                log.warn("[LegacySupply] {} attempt {}/{} failed: {}", operation, attempt, maxAttempts, e.describe());

                switch (e.kind()) {
                    case AUTH -> session.invalidate(token, e.describe()); // sign in again on the next try
                    case TRANSIENT -> {
                        if (attempt < maxAttempts) {
                            sleep(backoffFor(attempt));
                        }
                    }
                    case RATE_LIMITED -> {
                        pauseFor(Duration.ofSeconds(60));
                        throw e;
                    }
                    case CREDENTIALS -> {
                        pauseFor(Duration.ofMinutes(5));
                        throw e;
                    }
                    default -> throw e; // REJECTED, CONFLICT, NOT_FOUND: retrying won't change the answer
                }
            }
        }

        if (last != null && last.kind() == LegacySupplyException.Kind.TRANSIENT) {
            pauseFor(Duration.ofSeconds(20)); // looks like an outage - let the scheduled jobs wait a bit
        }
        throw last;
    }

    private long backoffFor(int attempt) {
        long exponential = baseBackoffMs * (1L << (attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(baseBackoffMs / 2 + 1);
        return exponential + jitter;
    }

    private void pauseFor(Duration duration) {
        pausedUntil = Instant.now().plus(duration);
        log.warn("[LegacySupply] Cooling down for {}s", duration.toSeconds());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
