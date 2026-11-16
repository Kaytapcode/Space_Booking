package com.spacebooking.testsupport;

import com.spacebooking.booking.service.BookingScheduler;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exists only under profile e2e; SecurityConfig permits /test-support/** only under the same profile. */
@RestController
@Profile("e2e")
@RequestMapping("/test-support")
public class TestSupportController {

    private final TestSupportService support;
    private final BookingScheduler scheduler;

    public TestSupportController(TestSupportService support, BookingScheduler scheduler) {
        this.support = support;
        this.scheduler = scheduler;
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset() {
        support.reset();
    }

    @PostMapping("/clock")
    ClockResponse clock(@RequestBody ClockRequest request) {
        return new ClockResponse(support.changeClock(request));
    }

    @PostMapping("/run-scheduler")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void runScheduler() {
        scheduler.run();
    }
}
