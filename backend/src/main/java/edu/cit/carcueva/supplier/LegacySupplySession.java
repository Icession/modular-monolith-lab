package edu.cit.carcueva.supplier;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

/**
 * Holds the current LegacySupply session token. Package-private.
 *
 * - Signs in lazily (only when a call actually needs a token).
 * - When LegacySupply rejects the token, invalidate() drops it so the next
 *   call signs in again automatically. No manual token pasting.
 * - Logs how long each session actually lasted - the manual only says
 *   "short-lived", so these log lines are how you measure it (INTEGRATION.md).
 */
@Component
class LegacySupplySession {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplySession.class);

    private final LegacySupplyHttp http;
    private final String clientId;
    private final String apiKey;

    private String token;
    private Instant obtainedAt;

    LegacySupplySession(
            LegacySupplyHttp http,
            @Value("${legacysupply.client-id}") String clientId,
            @Value("${legacysupply.api-key}") String apiKey) {
        this.http = http;
        this.clientId = clientId;
        this.apiKey = apiKey;
        if (clientId.isBlank() || apiKey.isBlank()) {
            log.warn("[LegacySupply] LS_CLIENT_ID / LS_API_KEY not set - reorders will stay PENDING until they are.");
        }
    }

    synchronized String currentToken() {
        if (token == null) {
            signIn();
        }
        return token;
    }

    /** Forget `rejectedToken` - but only if it's still the current one (another thread may have refreshed it). */
    synchronized void invalidate(String rejectedToken, String reason) {
        if (rejectedToken == null || !Objects.equals(rejectedToken, token)) {
            return;
        }
        long seconds = Duration.between(obtainedAt, Instant.now()).toSeconds();
        log.info("[LegacySupply] Session rejected after {}s ({}). Signing in again.", seconds, reason);
        token = null;
        obtainedAt = null;
    }

    private void signIn() {
        String body = http.send(HttpMethod.POST, "/auth/token",
                XmlCodec.authRequest(clientId, apiKey), Map.of());
        Document doc = XmlCodec.parse(body);
        String newToken = XmlCodec.text(doc, "SessionToken");
        if (newToken == null || newToken.isBlank()) {
            throw LegacySupplyException.malformed("AuthResponse without SessionToken");
        }
        token = newToken;
        obtainedAt = Instant.now();
        log.info("[LegacySupply] New session obtained (server IssuedAt {}).", XmlCodec.text(doc, "IssuedAt"));
    }
}
