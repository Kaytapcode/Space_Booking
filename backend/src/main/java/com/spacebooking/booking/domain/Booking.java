package com.spacebooking.booking.domain;

import com.spacebooking.room.domain.Room;
import com.spacebooking.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** All timestamps are UTC (schema.sql). State transitions are added by the domain layer. */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Booking(Room room, User user, String title, LocalDateTime startTime, LocalDateTime endTime,
                   BookingStatus status, Clock clock) {
        this.id = UUID.randomUUID();
        this.room = room;
        this.user = user;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.createdAt = LocalDateTime.now(clock);
    }

    // Package-private: only BookingState implementations change a booking.
    void setStatus(BookingStatus status) {
        this.status = status;
    }

    void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
