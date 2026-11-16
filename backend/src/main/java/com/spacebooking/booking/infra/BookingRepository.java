package com.spacebooking.booking.infra;

import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /**
     * Row lock for every transition (domain.md section 7); a wait longer than 3 s fails with a lock timeout.
     * No join fetch: FOR UPDATE would also lock the room and user rows. They load lazily inside the transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    /** PostgreSQL ignores the JPA lock.timeout hint for waits > 0; this sets it for the current transaction only. */
    @Query(value = "select set_config('lock_timeout', '3s', true)", nativeQuery = true)
    String applyLockTimeout();

    /** Section 2 predicate against blocking statuses (PENDING, CONFIRMED, CHECKED_IN). */
    @Query("""
            select count(b) > 0 from Booking b
            where b.room.id = :roomId
              and b.status in (com.spacebooking.booking.domain.BookingStatus.PENDING,
                               com.spacebooking.booking.domain.BookingStatus.CONFIRMED,
                               com.spacebooking.booking.domain.BookingStatus.CHECKED_IN)
              and b.startTime < :end and b.endTime > :start
            """)
    boolean existsOverlap(@Param("roomId") UUID roomId, @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);

    @Query("""
            select count(b) > 0 from Booking b
            where b.room.id = :roomId
              and b.status in (com.spacebooking.booking.domain.BookingStatus.PENDING,
                               com.spacebooking.booking.domain.BookingStatus.CONFIRMED,
                               com.spacebooking.booking.domain.BookingStatus.CHECKED_IN)
              and b.endTime > :now
            """)
    boolean existsBlockingEndingAfter(@Param("roomId") UUID roomId, @Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = {"room", "user"})
    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"room", "user"})
    Page<Booking> findByUserIdAndStatus(UUID userId, BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"room", "user"})
    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"room", "user"})
    @Query("""
            select b from Booking b
            where b.room.active = true
              and b.status in (com.spacebooking.booking.domain.BookingStatus.PENDING,
                               com.spacebooking.booking.domain.BookingStatus.CONFIRMED,
                               com.spacebooking.booking.domain.BookingStatus.CHECKED_IN)
              and b.startTime < :dayEnd and b.endTime > :dayStart
            order by b.startTime asc, b.id asc
            """)
    List<Booking> findForSchedule(@Param("dayStart") LocalDateTime dayStart, @Param("dayEnd") LocalDateTime dayEnd);

    /** Scheduler candidates (section 5); the per-id transition re-checks status and guard on the locked row. */
    @Query("""
            select b.id from Booking b
            where b.status = com.spacebooking.booking.domain.BookingStatus.PENDING and b.startTime <= :now
            order by b.startTime asc, b.id asc
            """)
    List<UUID> findIdsToExpire(@Param("now") LocalDateTime now);

    /** threshold = now - 15m, so `startTime < threshold` is the guard `now > start + 15m`. */
    @Query("""
            select b.id from Booking b
            where b.status = com.spacebooking.booking.domain.BookingStatus.CONFIRMED and b.startTime < :threshold
            order by b.startTime asc, b.id asc
            """)
    List<UUID> findIdsToMarkNoShow(@Param("threshold") LocalDateTime threshold);

    @Query("""
            select b.id from Booking b
            where b.status = com.spacebooking.booking.domain.BookingStatus.CHECKED_IN and b.endTime <= :now
            order by b.startTime asc, b.id asc
            """)
    List<UUID> findIdsToComplete(@Param("now") LocalDateTime now);
}
