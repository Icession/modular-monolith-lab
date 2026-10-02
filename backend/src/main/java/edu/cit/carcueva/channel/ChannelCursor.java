package edu.cit.carcueva.channel;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_cursor")
class ChannelCursor {
    static final int SINGLE_ROW = 1;

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChannelCursor() {
    }

    ChannelCursor(long lastSeq) {
        this.id = SINGLE_ROW;
        this.lastSeq = lastSeq;
        this.updatedAt = Instant.now();
    }

    long getLastSeq() {
        return lastSeq;
    }

    void moveTo(long seq) {
        this.lastSeq = seq;
        this.updatedAt = Instant.now();
    }
}
