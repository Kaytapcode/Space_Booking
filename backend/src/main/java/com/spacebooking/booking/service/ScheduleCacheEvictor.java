package com.spacebooking.booking.service;

import com.spacebooking.booking.domain.BookingStatusChangedEvent;
import com.spacebooking.room.domain.RoomChangedEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer for domain sections 8 and 10. After commit, not before: an eviction inside the transaction would let a
 * concurrent reader re-fill the cache with the pre-commit schedule.
 */
@Component
public class ScheduleCacheEvictor {

    public static final String CACHE = "dailySchedule";

    private final CacheManager cacheManager;

    public ScheduleCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingChanged(BookingStatusChangedEvent event) {
        clear();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomChanged(RoomChangedEvent event) {
        clear();
    }

    private void clear() {
        Cache cache = cacheManager.getCache(CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
}
