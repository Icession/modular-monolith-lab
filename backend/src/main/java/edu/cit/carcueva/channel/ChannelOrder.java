package edu.cit.carcueva.channel;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_orders")
class ChannelOrder {
    static final String NEW = "NEW";
    static final String ACCEPTED = "ACCEPTED";
    static final String REJECTED = "REJECTED";
    static final String BACKORDERED = "BACKORDERED";
    static final String BACKORDER_FILLED = "BACKORDER_FILLED";
    static final String BACKORDER_CANCELLED = "BACKORDER_CANCELLED";
    static final String CUSTOMER_CANCELLED = "CUSTOMER_CANCELLED";

    @Id
    @Column(name = "tiangge_order_id")
    private String tianggeOrderId;

    @Column(name = "shop_order_id")
    private Long shopOrderId;

    @Column(name = "lines", nullable = false)
    private String lines;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "decision")
    private String decision;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "decision_reported_at")
    private Instant decisionReportedAt;

    @Column(name = "resolution")
    private String resolution;

    @Column(name = "resolution_reported_at")
    private Instant resolutionReportedAt;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Column(name = "cancel_restocked")
    private Boolean cancelRestocked;

    @Column(name = "cancel_confirmed_at")
    private Instant cancelConfirmedAt;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "decision_deadline")
    private Instant decisionDeadline;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChannelOrder() {
    }

    ChannelOrder(String tianggeOrderId, String lines, Instant placedAt, Instant decisionDeadline) {
        this.tianggeOrderId = tianggeOrderId;
        this.lines = lines;
        this.state = NEW;
        this.placedAt = placedAt;
        this.decisionDeadline = decisionDeadline;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    void decided(Long shopOrderId, String state, String decision, String reason) {
        this.shopOrderId = shopOrderId;
        this.state = state;
        this.decision = decision;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
        touch();
    }

    void resolved(String state, String resolution) {
        this.state = state;
        this.resolution = resolution;
        touch();
    }

    void cancelledByCustomer(boolean restocked) {
        this.state = CUSTOMER_CANCELLED;
        this.cancelRequestedAt = Instant.now();
        this.cancelRestocked = restocked;
        touch();
    }

    void decisionReported() {
        this.decisionReportedAt = Instant.now();
        touch();
    }

    void resolutionReported() {
        this.resolutionReportedAt = Instant.now();
        touch();
    }

    void cancellationConfirmed() {
        this.cancelConfirmedAt = Instant.now();
        touch();
    }

    void recordError(String error) {
        this.lastError = error == null || error.length() <= 500 ? error : error.substring(0, 500);
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    String getTianggeOrderId() { return tianggeOrderId; }
    Long getShopOrderId() { return shopOrderId; }
    String getLines() { return lines; }
    String getState() { return state; }
    String getDecision() { return decision; }
    String getDecisionReason() { return decisionReason; }
    Instant getDecisionReportedAt() { return decisionReportedAt; }
    String getResolution() { return resolution; }
    Instant getResolutionReportedAt() { return resolutionReportedAt; }
    Instant getCancelRequestedAt() { return cancelRequestedAt; }
    Boolean getCancelRestocked() { return cancelRestocked; }
    Instant getCancelConfirmedAt() { return cancelConfirmedAt; }
    Instant getPlacedAt() { return placedAt; }
    Instant getDecisionDeadline() { return decisionDeadline; }
    Instant getDecidedAt() { return decidedAt; }
    Instant getCreatedAt() { return createdAt; }

    String shopOrderRef() {
        return shopOrderId == null ? null : "SO-" + shopOrderId;
    }
}
