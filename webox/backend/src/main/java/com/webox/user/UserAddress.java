package com.webox.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A delivery address remembered for a user, offered as a pick-list at checkout
 * (PRD 3.4: "select from a saved address or enter a new one").
 */
@Entity
@Table(name = "user_address")
@Getter
@Setter
@NoArgsConstructor
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "address_text", nullable = false, length = 200)
    private String addressText;

    @Column(name = "use_count", nullable = false)
    private int useCount = 1;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt = Instant.now();

    public UserAddress(Long userId, String addressText) {
        this.userId = userId;
        this.addressText = addressText;
    }
}
