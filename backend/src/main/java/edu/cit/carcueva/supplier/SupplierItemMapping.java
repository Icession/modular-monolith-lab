package edu.cit.carcueva.supplier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Translates OUR product IDs into LegacySupply's item numbers and pack sizes,
 * and our "units needed" into LegacySupply's "cases to order".
 *
 * Configured with one property (see application.properties):
 *   supplier.items=P100:ABC-1234:12,P200:XYZ-555:6,P300:QRS-9:10
 *   (ourProductId:SupplierSku:PackSize, comma-separated)
 */
@Component
class SupplierItemMapping {

    /** One mapped product. packSize = how many single units one LegacySupply Qty (one case) contains. */
    record SupplierItem(String productId, String supplierSku, int packSize) {

        /**
         * Units -> cases, ROUNDING UP (we'd rather receive a few extra than too few).
         * LegacySupply accepts Qty 1..99 only, so the result is clamped to that range.
         * Example: need 13 units, pack size 12 -> ceil(13 / 12) = 2 cases = 24 units.
         */
        int casesFor(int unitsNeeded) {
            int cases = (unitsNeeded + packSize - 1) / packSize;
            return Math.max(1, Math.min(cases, 99));
        }
    }

    private final Map<String, SupplierItem> items = new HashMap<>();

    SupplierItemMapping(@Value("${supplier.items:}") String spec) {
        for (String entry : spec.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(":");
            if (parts.length != 3) {
                throw new IllegalStateException("Bad supplier.items entry '" + trimmed
                        + "' - expected productId:SupplierSku:PackSize");
            }
            int packSize = Integer.parseInt(parts[2].trim());
            if (packSize < 1) {
                throw new IllegalStateException("Pack size must be >= 1 in '" + trimmed + "'");
            }
            items.put(parts[0].trim(), new SupplierItem(parts[0].trim(), parts[1].trim(), packSize));
        }
    }

    Optional<SupplierItem> find(String productId) {
        return Optional.ofNullable(items.get(productId));
    }
}
