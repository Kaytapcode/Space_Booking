package com.spacebooking.room.infra;

import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.room.domain.Room;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class RoomSpecifications {

    private RoomSpecifications() {
    }

    /** Filters are ANDed. Tokens are expected pre-validated against {@code ^[a-z0-9-]{1,24}$} (no LIKE wildcards). */
    public static Specification<Room> search(Integer minCapacity, List<String> equipments, LocalDateTime from,
                                             LocalDateTime to, boolean includeInactive) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (!includeInactive) {
                all.add(cb.isTrue(root.get("active")));
            }
            if (minCapacity != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity));
            }
            if (equipments != null) {
                // ",a,b," LIKE "%,a,%" matches whole tokens only: "tv" does not match "tvbox".
                var wrapped = cb.concat(cb.concat(",", root.<String>get("equipments")), ",");
                for (String token : equipments) {
                    all.add(cb.like(wrapped, "%," + token + ",%"));
                }
            }
            if (from != null && to != null) {
                Subquery<Long> overlap = query.subquery(Long.class);
                Root<Booking> b = overlap.from(Booking.class);
                overlap.select(cb.literal(1L)).where(
                        cb.equal(b.get("room"), root),
                        b.get("status").in(BookingStatus.BLOCKING),
                        cb.lessThan(b.get("startTime"), to),
                        cb.greaterThan(b.get("endTime"), from));
                all.add(cb.not(cb.exists(overlap)));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }
}
