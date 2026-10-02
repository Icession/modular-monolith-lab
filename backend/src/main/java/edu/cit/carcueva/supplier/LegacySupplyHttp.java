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

import edu.cit.carcueva.AppInstance;

@Component
class LegacySupplyHttp {
    private record RawResponse(int status, String body) {
    }

    private final RestClient restClient;
    private final String baseUrl;
    private final AppInstance appInstance;

    LegacySupplyHttp(
            AppInstance appInstance,
            @Value("${legacysupply.base-url}") String baseUrl,
            @Value("${legacysupply.timeout-ms:3000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.appInstance = appInstance;
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
                        h.set("X-Client-Instance", appInstance.id());
                        headers.forEach(h::set);
                    });
            if (xmlBody != null) {
                spec = spec.contentType(MediaType.APPLICATION_XML)
                        .body(xmlBody.getBytes(StandardCharsets.UTF_8));
            }
            response = spec.exchange((request, res) -> new RawResponse(
                    res.getStatusCode().value(),
                    new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            throw LegacySupplyException.transport(e.getMostSpecificCause());
        }

        if (response.status() >= 400) {
            throw LegacySupplyException.fromResponse(response.status(), response.body());
        }
        return response.body();
    }
}
