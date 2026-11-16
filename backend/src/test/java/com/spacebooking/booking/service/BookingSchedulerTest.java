package com.spacebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.spacebooking.booking.domain.BookingAction;
import com.spacebooking.booking.infra.BookingRepository;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;

/** R19-R22, R52, R53 (domain.md section 5). The repository and BookingService are collaborators, not the unit. */
class BookingSchedulerTest {

  private static final Instant NOW_INSTANT = Instant.parse("2031-01-10T03:00:00Z");
  private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

  private BookingRepository repository;
  private BookingService service;
  private BookingScheduler scheduler;

  @BeforeEach
  void setUp() {
    repository = mock(BookingRepository.class);
    service = mock(BookingService.class);
    scheduler = new BookingScheduler(repository, service, Clock.fixed(NOW_INSTANT, ZoneOffset.UTC));
  }

  // R19: PENDING with start <= now is a candidate; the scheduler asks with now and transitions every id with EXPIRE
  @Test
  void expiresPendingAtStart() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    when(repository.findIdsToExpire(NOW)).thenReturn(List.of(a, b));

    scheduler.run();

    verify(repository).findIdsToExpire(NOW);
    verify(service).systemTransition(a, BookingAction.EXPIRE);
    verify(service).systemTransition(b, BookingAction.EXPIRE);
    verify(service, never()).systemTransition(any(), eq(BookingAction.MARK_NO_SHOW));
    verify(service, never()).systemTransition(any(), eq(BookingAction.COMPLETE));
  }

  // R20: no-show only when now > start + 15m, i.e. start < now - 15m. The fake repository applies that predicate.
  @ParameterizedTest(name = "{0} min after start -> noShow={1}")
  @CsvSource({"0,false", "14,false", "15,false", "16,true", "60,true"})
  void noShow(long minutesAfterStart, boolean expectNoShow) {
    UUID id = UUID.randomUUID();
    LocalDateTime start = NOW.minusMinutes(minutesAfterStart);
    when(repository.findIdsToMarkNoShow(any()))
        .thenAnswer(inv -> start.isBefore(inv.<LocalDateTime>getArgument(0)) ? List.of(id) : List.of());

    scheduler.run();

    verify(repository).findIdsToMarkNoShow(NOW.minusMinutes(15));
    verify(service, times(expectNoShow ? 1 : 0)).systemTransition(id, BookingAction.MARK_NO_SHOW);
    verify(service, never()).systemTransition(any(), eq(BookingAction.EXPIRE));
    verify(service, never()).systemTransition(any(), eq(BookingAction.COMPLETE));
  }

  // R21: CHECKED_IN with end <= now completes
  @Test
  void completesAtEnd() {
    UUID id = UUID.randomUUID();
    when(repository.findIdsToComplete(NOW)).thenReturn(List.of(id));

    scheduler.run();

    verify(repository).findIdsToComplete(NOW);
    verify(service).systemTransition(id, BookingAction.COMPLETE);
    verify(service, never()).systemTransition(any(), eq(BookingAction.EXPIRE));
    verify(service, never()).systemTransition(any(), eq(BookingAction.MARK_NO_SHOW));
  }

  // R22: an exception for one booking neither aborts its phase nor the later phases
  @Test
  void continuesAfterFailure() {
    UUID bad = UUID.randomUUID();
    UUID good = UUID.randomUUID();
    UUID noShow = UUID.randomUUID();
    UUID complete = UUID.randomUUID();
    when(repository.findIdsToExpire(NOW)).thenReturn(List.of(bad, good));
    when(repository.findIdsToMarkNoShow(NOW.minusMinutes(15))).thenReturn(List.of(noShow));
    when(repository.findIdsToComplete(NOW)).thenReturn(List.of(complete));
    doThrow(new IllegalStateException("boom")).when(service).systemTransition(bad, BookingAction.EXPIRE);

    assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();

    verify(service).systemTransition(good, BookingAction.EXPIRE);
    verify(service).systemTransition(noShow, BookingAction.MARK_NO_SHOW);
    verify(service).systemTransition(complete, BookingAction.COMPLETE);
  }

  // R52: the locked re-check found the row no longer eligible (service throws INVALID_STATE_TRANSITION): skipped, not retried
  @Test
  void skipsWhenNoLongerEligible() {
    UUID stale = UUID.randomUUID();
    UUID next = UUID.randomUUID();
    when(repository.findIdsToMarkNoShow(NOW.minusMinutes(15))).thenReturn(List.of(stale, next));
    doThrow(new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "Cannot mark as no-show."))
        .when(service)
        .systemTransition(stale, BookingAction.MARK_NO_SHOW);

    assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();

    verify(service, times(1)).systemTransition(stale, BookingAction.MARK_NO_SHOW);
    verify(service).systemTransition(next, BookingAction.MARK_NO_SHOW);
    verifyNoMoreInteractions(service);
  }

  // R53: expire -> no-show -> complete
  @Test
  void runOrder() {
    UUID expire = UUID.randomUUID();
    UUID noShow = UUID.randomUUID();
    UUID complete = UUID.randomUUID();
    when(repository.findIdsToExpire(NOW)).thenReturn(List.of(expire));
    when(repository.findIdsToMarkNoShow(NOW.minusMinutes(15))).thenReturn(List.of(noShow));
    when(repository.findIdsToComplete(NOW)).thenReturn(List.of(complete));

    scheduler.run();

    InOrder order = inOrder(service);
    order.verify(service).systemTransition(expire, BookingAction.EXPIRE);
    order.verify(service).systemTransition(noShow, BookingAction.MARK_NO_SHOW);
    order.verify(service).systemTransition(complete, BookingAction.COMPLETE);
  }
}
