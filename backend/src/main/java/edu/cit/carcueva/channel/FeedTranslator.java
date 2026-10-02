package edu.cit.carcueva.channel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import com.fasterxml.jackson.databind.JsonNode;

final class FeedTranslator {
    private FeedTranslator() {
    }

    static List<FeedEvent> events(JsonNode page) {
        List<FeedEvent> events = new ArrayList<>();
        for (JsonNode e : page.path("events")) {
            List<FeedEvent.FeedLine> lines = new ArrayList<>();
            for (JsonNode line : e.path("lines")) {
                lines.add(new FeedEvent.FeedLine(line.path("sellerSku").asText(""), line.path("qty").asInt(0)));
            }
            events.add(new FeedEvent(
                    e.path("seq").asLong(0),
                    e.path("eventId").asText(null),
                    e.path("type").asText(""),
                    e.path("orderId").asText(null),
                    instant(e.path("placedAt")),
                    instant(e.path("decisionDeadline")),
                    instant(e.path("cancelledAt")),
                    lines));
        }
        return events;
    }

    static OptionalLong nextCursor(JsonNode page) {
        JsonNode next = page.path("nextCursor");
        return next.isNumber() || (next.isTextual() && next.asText().matches("\\d+"))
                ? OptionalLong.of(next.asLong())
                : OptionalLong.empty();
    }

    static Map<String, Integer> quantities(List<FeedEvent.FeedLine> lines) {
        Map<String, Integer> qty = new LinkedHashMap<>();
        for (FeedEvent.FeedLine line : lines) {
            if (!line.sellerSku().isBlank() && line.qty() > 0) {
                qty.merge(line.sellerSku(), line.qty(), Integer::sum);
            }
        }
        return qty;
    }

    static String encodeLines(Map<String, Integer> qty) {
        StringBuilder sb = new StringBuilder();
        qty.forEach((sku, n) -> {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(sku).append(':').append(n);
        });
        return sb.toString();
    }

    static Map<String, Integer> decodeLines(String encoded) {
        Map<String, Integer> qty = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return qty;
        }
        for (String part : encoded.split(",")) {
            int colon = part.lastIndexOf(':');
            if (colon > 0) {
                qty.merge(part.substring(0, colon), Integer.parseInt(part.substring(colon + 1)), Integer::sum);
            }
        }
        return qty;
    }

    private static Instant instant(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
