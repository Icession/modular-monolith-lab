package edu.cit.carcueva.channel;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ListedProducts {
    private final List<String> productIds;

    ListedProducts(@Value("${channel.listings:P100,P200,P300}") String spec) {
        this.productIds = Arrays.stream(spec.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    List<String> all() {
        return productIds;
    }

    boolean contains(String productId) {
        return productIds.contains(productId);
    }
}
