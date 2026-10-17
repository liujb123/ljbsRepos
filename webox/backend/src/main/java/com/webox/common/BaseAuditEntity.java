package com.webox.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Audit timestamps kept in UTC ({@code hibernate.jdbc.time_zone=UTC}).
 *
 * <p>Business dates (meal date, menu date) are separate {@code LocalDate} fields in the
 * business time zone; these columns only answer "when did this row change".
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseAuditEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
