# Decisions
`DEFAULT` = applied unless overridden at GATE 1. `APPROVED` = explicitly approved. GATE 1 approved by the user on 2026-11-16 with no overrides.

| ID | Gap | Resolution | Status |
|---|---|---|---|
| G1 | Who can register as what | Register always creates EMPLOYEE; MANAGER/ADMIN only via seeder | DEFAULT |
| G2 | ADMIN and bookings | ADMIN cannot create or act on bookings (403); reads any booking's logs and the global audit log | DEFAULT |
| G3 | Missing endpoints | Added `GET /auth/me`, `GET /bookings/pending`, `GET /audit-logs`, `GET /rooms/{id}`, `GET /rooms?includeInactive=true`; minimal Admin page (room CRUD + audit table) | DEFAULT |
| G4 | Scheduler scope | One job every 5 min: PENDING->CANCELLED (EXPIRED) at start, CONFIRMED->NO_SHOW after start+15m, CHECKED_IN->COMPLETED at end | DEFAULT |
| G5 | Locking | `OPTIMISTIC_FORCE_INCREMENT` on room in the creation transaction, plus DB backstop `EXCLUDE USING gist` with `btree_gist` (Neon lists `btree_gist` as supported). Conflict -> 409 `BOOKING_CONFLICT` | APPROVED |
| G6 | Time | UTC storage, `Asia/Ho_Chi_Minh` day boundaries, injected `Clock`, reject start before current minute (400 `START_IN_PAST`) | DEFAULT |
| G7 | Duration | EMPLOYEE <= 120 min inclusive; MANAGER unlimited | DEFAULT |
| G8 | Room deactivation | Blocking future bookings -> 409 `ROOM_HAS_FUTURE_BOOKINGS`; inactive rooms hidden and unbookable for non-admins (404) | DEFAULT |
| G9 | `equipments` format | Lowercase tokens; API array, DB comma-joined; whole-token AND filter | DEFAULT |
| G10 | Who may do what | Cancel owner-only, PENDING/CONFIRMED, before start; approve/reject MANAGER-only on PENDING, reject reason 1–255; check-in/checkout owner-only | DEFAULT |
| G11 | daily-schedule | Active rooms + blocking bookings of the day, booker `fullName` only; cache key = date, evicted on every mutation; warm < 50 ms | DEFAULT |
| G12 | Auth | Password >= 8, BCrypt, HS256 `JWT_SECRET` >= 32 bytes required outside dev/e2e, TTL default 8 h, CORS from env | DEFAULT |
| G13 | Seeder | Only with `app.seed.enabled=true`; prod passwords from env | DEFAULT |
| G14 | Schema management | Flyway `V1__init.sql` = `schema.sql`; `ddl-auto=validate` | DEFAULT |
| D15 | Register response | Returns `AuthResponse` (token + user) so the user is logged in at once | DEFAULT |
| D16 | Time granularity | Booking times must be whole minutes (400 otherwise) | DEFAULT |
| D17 | Early checkout | `end = min(end, max(start+1m, ceilToMinute(now)))`; keeps `end > start` when checking out before start | DEFAULT |
| D18 | Approve/reject after start | 409 `BOOKING_ALREADY_STARTED`; the scheduler expires it | DEFAULT |
| D19 | Room reactivation | `RoomRequest.active` on PUT: omitted = unchanged, `true` reactivates, `false` deactivates with the DELETE guard | DEFAULT |
| D20 | Inactive room on booking | 404 `ROOM_NOT_FOUND` (same as unknown room for non-admins) | DEFAULT |
| D21 | Ownership failure | 403 `ACCESS_DENIED` (not 404); booking ids are already visible in the daily schedule | DEFAULT |
| D22 | Room concurrent edit | Optimistic failure on room endpoints -> 409 `CONCURRENT_UPDATE`; booking creation -> `BOOKING_CONFLICT` | DEFAULT |
| D23 | Cache eviction | Evict all `dailySchedule` entries after commit (bookings may span days); rooms publish `RoomChangedEvent` to evict too. TTL 60 s bounds the accepted race where a reader loaded before commit re-fills the cache after eviction | DEFAULT |
| D24 | Audit actor | `performed_by` stores the actor email or `SYSTEM_SCHEDULER`; room changes are not audited | DEFAULT |
| D25 | Lists | `GET /rooms` and `GET /bookings/{id}/logs` are unpaged arrays (small, bounded); other lists use `page`/`size` (max 100) | DEFAULT |
| D26 | Not built | No `GET /bookings/{id}`, no booking edit, no booking horizon limit, no per-user double-booking check across rooms | DEFAULT |
| D27 | Stack additions | None beyond the pre-approved list | DEFAULT |
| D28 | Concurrent transitions on one booking | Every transition (user and scheduler) locks the booking row `PESSIMISTIC_WRITE` and re-checks state; the loser gets 409 `INVALID_STATE_TRANSITION`, scheduler skips. No schema change. Why not optimistic: transitions must not bump `rooms.version`, and the spec's objection targets the read-heavy room row, not one booking row held for milliseconds. Lock timeout 3 s (`jakarta.persistence.lock.timeout`); timeout -> 409 `CONCURRENT_UPDATE` | DEFAULT |
| D29 | Guard order | 401 -> role 403 (security layer) -> 400 -> 404 -> owner 403 -> 409; role before body so unauthorised roles learn nothing about validation. `includeInactive=true` by a non-admin is a role check: 403 even if other params are invalid | DEFAULT |
| D30 | Equipment token length | Max 24 chars x 20 tokens fits `VARCHAR(500)` | DEFAULT |
| D31 | Malformed path UUID | 400 `VALIDATION_FAILED` | DEFAULT |
| D32 | Sort tie-breaker | Every sorted list breaks ties by `id` asc | DEFAULT |
| D33 | Public operations and tokens | register/login ignore any `Authorization` header (a stale token never blocks login) | DEFAULT |
| D34 | Framework errors | Malformed JSON, type mismatch, missing param -> 400 `VALIDATION_FAILED`; unknown route 404, wrong method 405, unsupported media type 415 -> ProblemDetail `REQUEST_NOT_SUPPORTED` | DEFAULT |
| D35 | Error examples | Every operation declares each status it can return (incl. 500) with one example per error `code` and its own `instance` path | DEFAULT |
