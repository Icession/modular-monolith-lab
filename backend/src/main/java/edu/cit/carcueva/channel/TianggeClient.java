package edu.cit.carcueva.channel;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.cit.carcueva.AppInstance;

@Component
class TianggeClient {
    record Listing(String sellerSku, String title, String supplierSku) {
    }

    private final TianggeHttp http;
    private final ObjectMapper objectMapper;
    private final AppInstance appInstance;

    TianggeClient(TianggeHttp http, ObjectMapper objectMapper, AppInstance appInstance) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.appInstance = appInstance;
    }

    JsonNode heartbeat() {
        ObjectNode body = objectMapper.createObjectNode()
                .put("appName", "carcueva-shop")
                .put("startedAt", appInstance.startedAt().toString())
                .put("uptimeSeconds", appInstance.uptimeSeconds());
        return http.send(HttpMethod.POST, "/instances/heartbeat", body);
    }

    void publishListings(List<Listing> listings) {
        ArrayNode body = objectMapper.createArrayNode();
        for (Listing l : listings) {
            body.addObject()
                    .put("sellerSku", l.sellerSku())
                    .put("title", l.title())
                    .put("supplierSku", l.supplierSku());
        }
        http.send(HttpMethod.PUT, "/listings", body);
    }

    void publishStock(Map<String, Integer> available) {
        ArrayNode body = objectMapper.createArrayNode();
        available.forEach((sku, qty) -> body.addObject()
                .put("sellerSku", sku)
                .put("available", Math.max(qty, 0)));
        http.send(HttpMethod.PUT, "/stock", body);
    }

    JsonNode readFeed(long after, int limit) {
        return http.send(HttpMethod.GET, "/feed?after=" + after + "&limit=" + limit, null);
    }

    void sendDecision(String orderId, String decision, String shopOrderId, String reason) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("decision", decision)
                .put("shopOrderId", shopOrderId);
        if (reason != null && !reason.isBlank()) {
            body.put("reason", reason.length() > 200 ? reason.substring(0, 200) : reason);
        }
        http.send(HttpMethod.POST, "/orders/" + orderId + "/decision", body);
    }

    void sendResolution(String orderId, String status) {
        ObjectNode body = objectMapper.createObjectNode().put("status", status);
        http.send(HttpMethod.POST, "/orders/" + orderId + "/resolution", body);
    }

    void confirmCancellation(String orderId, boolean restocked) {
        ObjectNode body = objectMapper.createObjectNode().put("restocked", restocked);
        http.send(HttpMethod.POST, "/orders/" + orderId + "/cancellation", body);
    }
}
