package com.spacebooking.testsupport;

import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.seed.DataSeeder;
import java.time.Instant;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** E2E-only state control. Not a domain service: it deliberately bypasses the domain rules. */
@Service
@Profile("e2e")
public class TestSupportService {

    private final JdbcTemplate jdbc;
    private final DataSeeder seeder;
    private final E2eClock clock;
    private final CacheManager caches;

    public TestSupportService(JdbcTemplate jdbc, DataSeeder seeder, E2eClock clock, CacheManager caches) {
        this.jdbc = jdbc;
        this.seeder = seeder;
        this.clock = clock;
        this.caches = caches;
    }

    /** Truncate + reseed restores seed rooms exactly, whatever admin tests edited or deactivated. */
    @Transactional
    public void reset() {
        jdbc.execute("TRUNCATE TABLE audit_logs, bookings, rooms, users CASCADE");
        seeder.seed();
        clock.reset();
        clearCaches();
    }

    public Instant changeClock(ClockRequest req) {
        int modes = (req.instant() != null ? 1 : 0)
                + (req.advanceSeconds() != null ? 1 : 0)
                + (Boolean.TRUE.equals(req.reset()) ? 1 : 0);
        if (modes != 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Provide exactly one of instant, advanceSeconds, reset=true");
        }
        if (req.instant() != null) {
            clock.set(req.instant());
        } else if (req.advanceSeconds() != null) {
            clock.advance(req.advanceSeconds());
        } else {
            clock.reset();
        }
        clearCaches();
        return clock.instant();
    }

    // The daily schedule cache is keyed by date, which depends on "today".
    private void clearCaches() {
        caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
    }
}
