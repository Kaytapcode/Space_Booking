package com.spacebooking.booking.service;

import com.spacebooking.booking.domain.BookingAction;
import com.spacebooking.booking.infra.BookingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Domain section 5. Not transactional: each id gets its own transaction so one failure cannot roll back the rest. */
@Component
public class BookingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingScheduler.class);

    private final BookingRepository bookings;
    private final BookingService service;
    private final Clock clock;

    public BookingScheduler(BookingRepository bookings, BookingService service, Clock clock) {
        this.bookings = bookings;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(fixedRate = 300000)
    public void run() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        apply(bookings.findIdsToExpire(now), BookingAction.EXPIRE);
        apply(bookings.findIdsToMarkNoShow(now.minusMinutes(15)), BookingAction.MARK_NO_SHOW);
        apply(bookings.findIdsToComplete(now), BookingAction.COMPLETE);
    }

    private void apply(List<UUID> ids, BookingAction action) {
        for (UUID id : ids) {
            try {
                service.systemTransition(id, action);
            } catch (RuntimeException e) {
                log.warn("Scheduler skipped booking {} ({}): {}", id, action, e.getMessage());
            }
        }
    }
}
