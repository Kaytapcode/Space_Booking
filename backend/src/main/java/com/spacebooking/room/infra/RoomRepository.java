package com.spacebooking.room.infra;

import com.spacebooking.room.domain.Room;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    List<Room> findByActiveTrueOrderByNameAscIdAsc();

    /** Booking-creation lock (domain.md section 7): commit bumps the version, so concurrent creators collide. */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForBooking(@Param("id") UUID id);
}
