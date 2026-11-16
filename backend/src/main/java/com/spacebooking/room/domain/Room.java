package com.spacebooking.room.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private int capacity;

    /** Sorted, comma-joined lowercase tokens; empty string when none (domain.md section 11). */
    @Column(nullable = false)
    private String equipments;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    // Wrapper type so Spring Data treats a fresh instance as new; the locking protocol bumps it on booking.
    @Version
    private Long version;

    public Room(String name, String location, int capacity, String equipments, boolean requiresApproval) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.location = location;
        this.capacity = capacity;
        this.equipments = equipments;
        this.requiresApproval = requiresApproval;
        this.active = true;
    }

    public void update(String name, String location, int capacity, String equipmentsColumn, boolean requiresApproval) {
        this.name = name;
        this.location = location;
        this.capacity = capacity;
        this.equipments = equipmentsColumn;
        this.requiresApproval = requiresApproval;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
