package edu.cit.carcueva.supplier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * One raw HTTP call to LegacySupply, with a hard timeout. No retries here
 * (LegacySupplyClient does that). Package-private.
 *
 * Returns the response body for 2xx; throws LegacySupplyException for
 * everything else - including timeouts, which is how "slow" becomes
 * "failed, try again" instead of hanging the app.
 */
@Component
class LegacySupplyHttp {

    private record RawResponse(int status, String body) {
    }

    private final RestClient restClient;
    private final String baseUrl;

    LegacySupplyHttp(
            @Value("${legacysupply.base-url}") String baseUrl,
            @Value("${legacysupply.timeout-ms:3000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    String send(HttpMethod method, String path, String xmlBody, Map<String, String> headers) {
        RawResponse response;
        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(baseUrl + path)
                    .headers(h -> {
                        h.setAccept(List.of(MediaType.APPLICATION_XML));
                        headers.forEach(h::set);
                    });
            if (xmlBody != null) {
                // Sent as raw bytes so the Content-Type stays exactly "application/xml"
                spec = spec.contentType(MediaType.APPLICATION_XML)
                        .body(xmlBody.getBytes(StandardCharsets.UTF_8));
            }
            response = spec.exchange((request, res) -> new RawResponse(
                    res.getStatusCode().value(),
                    new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            // Timeouts (SocketTimeoutException), refused connections, DNS failures...
            throw LegacySupplyException.transport(e.getMostSpecificCause());
        }

        if (response.status() >= 400) {
            throw LegacySupplyException.fromResponse(response.status(), response.body());
        }
        return response.body();
    }
}
