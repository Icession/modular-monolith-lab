package edu.cit.carcueva.supplier;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One reorder we placed (or are trying to place) with the supplier.
 *
 * request_id is generated ONCE when the row is created and saved, so every
 * send of this reorder - retries, scheduled re-sends, even after an app
 * restart - carries the same X-Request-Id. That's what stops LegacySupply
 * from creating a duplicate purchase order.
 */
@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "buyer_ref")
    private String buyerRef;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "po_number")
    private String poNumber;

    /** How many supplier packs we ordered (LegacySupply's Qty). */
    @Column(name = "cases", nullable = false)
    private int cases;

    /** How many single units that is (cases x pack size) - what Inventory gets on delivery. */
    @Column(name = "units", nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SupplierOrderStatus status;

    /** How many times we have tried to send it. > 0 means it MAY already exist at LegacySupply. */
    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupplierOrder() {
        // JPA
    }

    SupplierOrder(String productId, String requestId, int cases, int units) {
        this.productId = productId;
        this.requestId = requestId;
        this.cases = cases;
        this.units = units;
        this.status = SupplierOrderStatus.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    void assignBuyerRef(String buyerRef) {
        this.buyerRef = buyerRef;
        touch();
    }

    void recordAttempt() {
        this.attempts++;
        touch();
    }

    void markSubmitted(String poNumber, SupplierOrderStatus status, String note) {
        this.poNumber = poNumber;
        this.status = status;
        this.lastError = note;
        touch();
    }

    void changeStatus(SupplierOrderStatus status, String note) {
        this.status = status;
        this.lastError = note;
        touch();
    }

    void recordError(String error) {
        this.lastError = error;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    Long getId() { return id; }
    String getProductId() { return productId; }
    String getBuyerRef() { return buyerRef; }
    String getRequestId() { return requestId; }
    String getPoNumber() { return poNumber; }
    int getCases() { return cases; }
    int getUnits() { return units; }
    SupplierOrderStatus getStatus() { return status; }
    int getAttempts() { return attempts; }
    String getLastError() { return lastError; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
