package edu.cit.carcueva.channel;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.cit.carcueva.AppInstance;

@Component
class TianggeHttp {
    private static final Logger log = LoggerFactory.getLogger(TianggeHttp.class);

    private record RawResponse(int status, String body) {
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppInstance appInstance;
    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final int maxAttempts;
    private final long backoffMs;

    TianggeHttp(
            ObjectMapper objectMapper,
            AppInstance appInstance,
            @Value("${tiangge.base-url}") String baseUrl,
            @Value("${tiangge.client-id}") String clientId,
            @Value("${tiangge.api-key}") String apiKey,
            @Value("${tiangge.timeout-ms:3000}") int timeoutMs,
            @Value("${tiangge.max-attempts:3}") int maxAttempts,
            @Value("${tiangge.backoff-ms:300}") long backoffMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.appInstance = appInstance;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
        if (clientId.isBlank() || apiKey.isBlank()) {
            log.warn("[Tiangge] LS_CLIENT_ID / LS_API_KEY not set - the shop cannot go live until they are.");
        }
    }

    JsonNode send(HttpMethod method, String path, Object body) {
        TianggeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return sendOnce(method, path, body);
            } catch (TianggeException e) {
                last = e;
                if (e.kind() != TianggeException.Kind.TRANSIENT || attempt == maxAttempts) {
                    throw e;
                }
                log.warn("[Tiangge] {} {} attempt {}/{} failed: {}", method, path, attempt, maxAttempts, e.describe());
                sleep(backoffMs * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(backoffMs / 2 + 1));
            }
        }
        throw last;
    }

    private JsonNode sendOnce(HttpMethod method, String path, Object body) {
        RawResponse response;
        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(baseUrl + path)
                    .headers(h -> {
                        h.setAccept(List.of(MediaType.APPLICATION_JSON));
                        h.set("X-Client-Id", clientId);
                        h.set("Authorization", "Bearer " + apiKey);
                        h.set("X-Client-Instance", appInstance.id());
                    });
            if (body != null) {
                spec = spec.contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsBytes(body));
            }
            response = spec.exchange((request, res) -> new RawResponse(
                    res.getStatusCode().value(),
                    new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            throw TianggeException.transport(e.getMostSpecificCause());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not write request body", e);
        }

        if (response.status() >= 400) {
            JsonNode error = parseQuietly(response.body());
            String code = error.path("error").asText(null);
            String message = error.path("message").asText(snippet(response.body()));
            throw TianggeException.fromResponse(response.status(), code, message);
        }
        return parse(response.body());
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw TianggeException.malformed(e.getMessage());
        }
    }

    private JsonNode parseQuietly(String body) {
        try {
            return parse(body);
        } catch (TianggeException e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String snippet(String body) {
        if (body == null) {
            return "";
        }
        String s = body.strip();
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
