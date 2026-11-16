# SpaceBooking — Domain Rules
Source: `docs/SpaceBooking.md` + `DECISIONS.md`. Shapes and codes: `openapi.yaml`. `now` = injected `Clock`.

## 1. Time
| Rule | Value |
|---|---|
| Storage | `TIMESTAMP` columns hold UTC; Java uses `Instant` |
| Business timezone | `Asia/Ho_Chi_Minh` (UTC+7, no DST): day boundaries, `date` params, UI display |
| Granularity, interval | Whole minutes (seconds or fractions != 0 -> 400 `VALIDATION_FAILED`); `[start, end)` with `end > start` |
| Start guard | `start < truncateToMinute(now)` -> 400 `START_IN_PAST` |
| Day range | `date` -> `[date 00:00+07, date+1 00:00+07)`; a booking belongs to a day if it overlaps that range |

## 2. Overlap predicate
`new.start < old.end AND new.end > old.start`, same `room_id`, `old.status IN (PENDING, CONFIRMED, CHECKED_IN)` (= **blocking statuses**).
Adjacent slots (09:00–10:00, 10:00–11:00) do not overlap; CANCELLED, NO_SHOW, COMPLETED never block. Used by creation, availability filter, daily schedule, deactivation guard.

## 3. Booking policy (Strategy `BookingPolicyStrategy`, chosen by role)
| Policy | Max duration | Initial status | Roles |
|---|---|---|---|
| `EmployeePolicy` | 120 min inclusive, else 400 `DURATION_EXCEEDED` | `room.requiresApproval ? PENDING : CONFIRMED` | EMPLOYEE |
| `ManagerPolicy` | none | CONFIRMED | MANAGER |
| — | — | creating or acting on bookings -> 403 `ACCESS_DENIED` | ADMIN |

## 4. State machine (State `BookingState`, one class per status)
| From | Action | Actor | Guard (else error) | To | Side effects | Audit action |
|---|---|---|---|---|---|---|
| — | create | EMPLOYEE, MANAGER | room active (404 `ROOM_NOT_FOUND`); start guard (400 `START_IN_PAST`); policy; no overlap (409 `BOOKING_CONFLICT`) | PENDING / CONFIRMED | room locked (§7) | CREATED |
| PENDING | approve | MANAGER | `now < start` (409 `BOOKING_ALREADY_STARTED`) | CONFIRMED | — | APPROVED |
| PENDING | reject | MANAGER | reason 1–255 non-blank (400, body step); `now < start` (409 `BOOKING_ALREADY_STARTED`) | CANCELLED | `rejection_reason = reason` | REJECTED |
| PENDING | cancel | owner | `now < start` (409 `BOOKING_ALREADY_STARTED`) | CANCELLED | — | CANCELLED |
| PENDING | expire | SYSTEM_SCHEDULER | `now >= start` | CANCELLED | — | EXPIRED |
| CONFIRMED | check-in | owner | `start - 15m <= now <= start + 15m` (409 `OUTSIDE_CHECKIN_WINDOW`) | CHECKED_IN | — | CHECKED_IN |
| CONFIRMED | cancel | owner | `now < start` (409 `BOOKING_ALREADY_STARTED`) | CANCELLED | — | CANCELLED |
| CONFIRMED | mark no-show | SYSTEM_SCHEDULER | `now > start + 15m` | NO_SHOW | — | NO_SHOW |
| CHECKED_IN | checkout | owner | — | COMPLETED | `end = min(end, max(start + 1m, ceilToMinute(now)))` | CHECKED_OUT |
| CHECKED_IN | complete | SYSTEM_SCHEDULER | `now >= end` | COMPLETED | — | COMPLETED |
Every other (status, action) pair -> 409 `INVALID_STATE_TRANSITION`. Terminal: CANCELLED, NO_SHOW, COMPLETED.
Every transition: lock the booking row (§7), publish `BookingStatusChangedEvent` (§8), evict the schedule cache after commit (§10).
Guard order: auth (401) -> role (403, security layer) -> request shape incl. path UUIDs (400) -> exists (404) -> owner (403) -> state (409) -> time guard (409/400). Unexpected errors -> 500 `INTERNAL_ERROR`, no stack trace.

## 5. Scheduler
One `@Scheduled(fixedRate = 300000)` job, actor `SYSTEM_SCHEDULER`. Order per run: expire PENDING -> mark NO_SHOW -> complete CHECKED_IN.
One transaction per booking: lock row, re-check status and guard, skip if no longer eligible. A failure is logged and does not stop the run. Between runs a stale booking keeps blocking; user actions still apply their own guards.

## 6. Early checkout
`end_time` = `now` rounded up to the next minute, clamped to `[start + 1m, original end]`; the freed tail is bookable at once. Audit `details` carries the new `endTime`.

## 7. Locking protocol
**Transitions** (user and scheduler): load the booking with `@Lock(PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`), then run the §4 state/guard checks on the locked row.
A concurrent second transition waits, then sees the new status and gets 409 `INVALID_STATE_TRANSITION` (scheduler: skips). Lock timeout 3 s -> 409 `CONCURRENT_UPDATE`. One audit row per winning transition.
**Creation:**
1. `@Transactional` service method (READ COMMITTED); role already checked by the security layer.
2. Load room with `@Lock(OPTIMISTIC_FORCE_INCREMENT)`; missing or inactive -> 404 `ROOM_NOT_FOUND`.
3. Apply start guard (§1) and policy (§3); then overlap query (§2) for the room, hit -> 409 `BOOKING_CONFLICT`.
4. Insert booking; publish event (audit row in the same transaction).
5. Commit increments `rooms.version`; a concurrent creator fails with `ObjectOptimisticLockingFailureException` -> 409 `BOOKING_CONFLICT`.
6. Backstop: exclusion constraint `ex_bookings_no_overlap` (SQLState `23P01`) -> 409 `BOOKING_CONFLICT`.
Room update/deactivation bumps `version` too, so it cannot race a booking; optimistic failure on a room endpoint -> 409 `CONCURRENT_UPDATE`. Transitions add no occupancy and do not lock the room.

## 8. Events -> audit (Observer)
| Event | Listener | Effect |
|---|---|---|
| `BookingStatusChangedEvent(bookingId, from, to, action, performedBy, details, occurredAt)` | `@EventListener` (sync, same transaction) | insert `audit_logs` row |
| `BookingStatusChangedEvent`, `RoomChangedEvent(roomId)` | `@TransactionalEventListener(AFTER_COMMIT)` | evict all `dailySchedule` entries |
`performed_by` = actor email or `SYSTEM_SCHEDULER`. `details` (NOT NULL) = JSON text `{"from","to"}` (+ `reason` on reject, `endTime` on checkout; `from` null on CREATED). Room changes are not audited.

## 9. Permission matrix (ownership in brackets)
| Endpoint | none | EMPLOYEE | MANAGER | ADMIN |
|---|---|---|---|---|
| POST /auth/register, POST /auth/login | yes | yes | yes | yes |
| GET /auth/me | 401 | yes | yes | yes |
| GET /rooms | 401 | yes | yes | yes |
| GET /rooms?includeInactive=true | 401 | 403 | 403 | yes |
| GET /rooms/{id} | 401 | yes (active only, else 404) | yes (active only, else 404) | yes |
| POST /rooms, PUT /rooms/{id}, DELETE /rooms/{id} | 401 | 403 | 403 | yes |
| GET /bookings/daily-schedule | 401 | yes | yes | yes |
| GET /bookings/my-bookings | 401 | yes [own] | yes [own] | 403 |
| GET /bookings/pending | 401 | 403 | yes | 403 |
| POST /bookings | 401 | yes | yes | 403 |
| PATCH /bookings/{id}/approve, /reject | 401 | 403 | yes [any] | 403 |
| PATCH /bookings/{id}/check-in, /checkout, /cancel | 401 | yes [owner, else 403] | yes [owner, else 403] | 403 |
| GET /bookings/{id}/logs | 401 | yes [owner, else 403] | yes [any] | yes [any] |
| GET /audit-logs | 401 | 403 | 403 | yes |

## 10. Cache
| Cache | Key | Value | Config | Evicted by |
|---|---|---|---|---|
| `dailySchedule` | `LocalDate` date | `DailyScheduleResponse` | Caffeine, TTL 60 s, maxSize 400 | all entries, after commit (§8): booking creation, every transition (user or scheduler), room create/update/deactivate |
Target: warm `daily-schedule` < 50 ms. Schedule and list queries use `@EntityGraph` (room, user); no N+1.
Sorts: rooms by name; schedule bookings by start; my-bookings start desc; pending start asc; logs created asc; audit-logs created desc; ties broken by `id` asc.

## 11. Rooms
| Rule | Value |
|---|---|
| `equipments` | API: array of tokens `^[a-z0-9-]{1,24}$`, unique, max 20 (fits VARCHAR(500)). DB: sorted, comma-joined, `''` when empty |
| Filter | whole-token match, all requested tokens required (`tv` does not match `tvbox`) |
| Availability filter | `availableFrom` + `availableTo` together and `availableTo > availableFrom` (else 400); excludes rooms with a blocking overlap |
| Inactive | not listed and not bookable for non-admins (404); existing bookings keep their status. PUT `active` omitted = unchanged |
| Deactivate guard | blocking booking with `end > now` exists -> 409 `ROOM_HAS_FUTURE_BOOKINGS` |
| Name | unique, case-sensitive; duplicate -> 409 `ROOM_NAME_ALREADY_EXISTS` |

## 12. Auth
Register always creates EMPLOYEE; email trimmed and lower-cased; duplicate -> 409 `EMAIL_ALREADY_EXISTS`. Password 8–72 chars, BCrypt.
JWT HS256, `JWT_SECRET` (>= 32 bytes; required outside `dev`/`e2e`), lifetime `JWT_TTL` (default 8 h), claims `sub`, `email`, `role`, `exp`. CORS origins from `CORS_ALLOWED_ORIGINS`. Bad login -> 401 `INVALID_CREDENTIALS`, same message for unknown email and wrong password.

## 13. Seed (`app.seed.enabled=true`; default on in dev/e2e, off in prod)
| Email | Role | Password (dev/e2e) | Full name |
|---|---|---|---|
| admin@spacebooking.local | ADMIN | `Admin@12345` | Quản trị viên |
| manager@spacebooking.local | MANAGER | `Manager@12345` | Lê Minh Quân |
| employee@spacebooking.local | EMPLOYEE | `Employee@12345` | Trần Thị Bình |
In prod the passwords come from `SEED_ADMIN_PASSWORD`, `SEED_MANAGER_PASSWORD`, `SEED_EMPLOYEE_PASSWORD`; the seeder skips existing emails and names.

| Room | Location | Capacity | Equipments | Requires approval |
|---|---|---|---|---|
| Phòng Sen | Tầng 3 | 6 | tv,whiteboard | false |
| Phòng Mai | Tầng 3 | 4 | whiteboard | false |
| Phòng Cúc | Tầng 2 | 2 | tvbox | false |
| Phòng Trường Sơn | Tầng 5 | 12 | tv,video-conference,whiteboard | true |
| Hội trường Bạch Đằng | Tầng 1 | 40 | microphone,projector,speaker | true |
