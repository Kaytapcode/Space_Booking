# SpaceBooking — Phase Plan (Phases 2–7)

Run one phase per `/next`. The contract in `docs/contract/` is frozen. Operating rules are in `CLAUDE.md`. Command aliases (BE verify, FE build, FE test, E2E, smoke) are defined in the `CLAUDE.md` command table; update that table when a command changes.
Delegate by file path. Parallel work is allowed only across disjoint owned directories, and at most 3 agents at once.

## Phase 2 — Backend foundation and security
**Agents:** backend-dev; test-engineer in parallel for security tests. In parallel, frontend-dev may run Phase 5.0 (design system) because it does not depend on the backend.
**Work:** Spring Boot 3.3 + Java 21 scaffold (Web, Data JPA, Security, Validation, PostgreSQL, Caffeine, Flyway, springdoc) with Maven wrapper, `docker-compose.yml` with `postgres:16`, `V1__init.sql`, 4 entities and repositories, JWT filter + BCrypt, `@PreAuthorize` per permission matrix, `@RestControllerAdvice` (all errors including 401/403/validation), seeder per G13, `/auth/register`, `/auth/login`, `/auth/me`.
**Gate:**
- BE verify passes (compiles, Flyway applies on Testcontainers, Hibernate `validate` passes).
- Integration tests over real HTTP (`RANDOM_PORT`, full security chain): register → login → `/auth/me`; wrong password 401; missing/invalid/expired token 401; role-forbidden 403; validation 400 with `errors[]`; every error body is ProblemDetail with `code`.
- Register cannot produce MANAGER or ADMIN.
- App refuses to start without `JWT_SECRET` outside dev/e2e.

## Phase 3 — Domain core
**Agents:** test-engineer writes failing tests first from `domain.md` and `test-plan.md` (red); domain-engineer implements until green; backend-dev does controllers, DTO mapping, rooms CRUD and the search Specification. Reviewer at the end.
**Work:** Specification (rooms search by capacity, equipment tokens, free time window); Strategy (`BookingPolicyStrategy` for Employee/Manager); State (one class per status); booking service with the locking protocol; `BookingStatusChangedEvent` + audit listener in the same transaction; all booking endpoints; room soft delete rule (G8); `ContractConformanceTest` comparing `/v3/api-docs` to `docs/contract/openapi.yaml` (method+path, status codes, schema properties, required, types, enums). Any diff fails the build.
**Gate:**
- BE verify passes.
- Overlap parameterized tests cover every boundary in `testing.md`.
- State × action matrix test: every illegal pair returns 409 `INVALID_STATE_TRANSITION`.
- Policy tests: Employee at 120 min passes, 121 fails `DURATION_EXCEEDED`; Manager unlimited and auto-CONFIRMED; Employee on `requires_approval` room → PENDING.
- Authorization matrix: every endpoint × {none, EMPLOYEE, MANAGER, ADMIN} plus ownership/IDOR cases, all as expected.
- **Concurrency:** ≥ 10 threads book the same room and slot: exactly one 201, the rest 409 `BOOKING_CONFLICT`, exactly one active booking row. Two threads on different rooms both succeed. Run this test class 5 times in a loop with no failure.
- Exactly one audit row per transition with the right `performed_by`.
- Conformance test passes.
- `reviewer` re-runs the gate itself and attempts: employee acting on another user's booking, employee calling manager endpoints, booking in an inactive room, and replaying a stale token.

## Phase 4 — Performance, cache, scheduler
**Agents:** domain-engineer; test-engineer.
**Work:** `@EntityGraph` on list queries, Caffeine (`maximumSize`, TTL) on daily-schedule, `@CacheEvict` on every mutation, scheduler per G4 (`@Scheduled(fixedRate = 300000)` calling a service method that takes the injected `Clock`), JaCoCo with `check` bound to `verify`.
**Gate:**
- BE verify passes **and** JaCoCo fails the build below 80% line coverage on `**/service/**` and `**/domain/**`; report the actual numbers.
- N+1: with Hibernate statistics, the query count for daily-schedule and my-bookings is identical for 5 and 50 bookings.
- Cache: a mutation by any path (including scheduler) never leaves stale data in the next read; warm daily-schedule p95 < 50 ms over 200 calls with ≥ 50 seeded bookings (measured in a test).
- Scheduler tests at exact boundaries (start + 15 min, end, start for PENDING), each writing an audit row with `SYSTEM_SCHEDULER`.

## Phase 5 — Frontend
**Agents:** frontend-dev; test-engineer for unit specs.
**5.0 Design system (can start in Phase 2):** use ui-ux-pro-max and frontend-design once to write `docs/design/DESIGN.md` (≤ 1 page): palette, type scale and font (Vietnamese subset), spacing, radii, status colours, component list, desktop timeline layout, mobile card layout, empty/error states. Later agents read only this file.
**5.1 Build:** Angular 17+ standalone + Tailwind. Generate API types from `docs/contract/openapi.yaml` with `openapi-typescript`. Thin API services, `AuthInterceptor` (Bearer token; ProblemDetail → toast with a Vietnamese message mapped from `code`), `AuthGuard` and role guard. Screens: Login/Register; Dashboard (Timeline via CSS Grid at ≥ 1024px, vertical cards with one-tap booking below that); Booking form (reactive form, custom validator end > start, room search filters, Employee 2-hour hint); My bookings (paginated; check-in, checkout, cancel with confirm); Approvals (MANAGER, reject needs a reason); Admin (rooms CRUD, audit log table).
**Gate:**
- FE build (production) passes with no warnings; lint passes.
- FE test passes: validator, interceptor error mapping, guards, status-to-badge mapping.
- No hand-written DTO types (grep proves all API types come from the generated file).
- `reviewer` checks DESIGN.md compliance and the anti-slop list in `frontend.md`.

## Phase 6 — End-to-end tests as a real user
**Agent:** e2e-qa.
**Work:** `e2e/` with `@playwright/test`; `webServer` starts Postgres (compose), backend with profile `e2e`, and the frontend. Under `@Profile("e2e")` only: `POST /test-support/reset` (reseed), `POST /test-support/clock` (set/advance the injected `Clock`), `POST /test-support/run-scheduler`. A test asserts these beans are absent in the default profile. Projects: desktop Chromium 1280×800 and mobile emulation 390×844. `workers: 1`, `retries: 0`, trace and screenshot on failure.
**Scenarios (every action through the UI):**
1. Unauthenticated visit to a protected route redirects to login; an Employee visiting `/approvals` or `/admin` is blocked.
2. Employee logs in, opens the dashboard, books a normal room through the form; the booking shows CONFIRMED in My bookings.
3. Employee books a `requires_approval` room → PENDING. Manager logs in, approves from the approvals page; Employee then sees CONFIRMED.
4. Manager rejects with a reason → Employee sees CANCELLED and the reason.
5. Conflict: two browser contexts book the same slot; one succeeds, the other sees the conflict message from `BOOKING_CONFLICT`. An adjacent slot (10:00 after 09:00–10:00) succeeds.
6. Employee tries 2h01m → form blocks it; Manager books the same duration successfully.
7. Check-in inside the window (clock = start − 10 min) works; check-in outside it is refused with the right message.
8. Early checkout frees the remainder: another user books the freed time.
9. NO_SHOW: advance clock to start + 16 min, run scheduler → status NO_SHOW and the slot is bookable again.
10. Cancel own booking; cannot cancel someone else's (no button, and the API refuses).
11. Admin creates, edits, deactivates a room (deactivation blocked when future bookings exist) and finds the matching rows in the audit table.
12. Responsive: at 1280px the timeline grid is visible; at 390px cards are shown, there is no horizontal scroll, and one tap starts a booking. Axe reports no serious or critical violations on Dashboard, Booking form, My bookings.
Also write `scripts/smoke.mjs`: a black-box HTTP script against a running server (login → search → book → approve → check-in → checkout → read logs) that exits non-zero on any mismatch.
**Gate:**
- E2E passes **twice in a row** with `retries: 0`. Any flake gets a root-cause fix, not a retry.
- `smoke.mjs` passes against the running backend.
- Visual check by the playwright plugin: at most 4 screenshots (desktop dashboard, mobile dashboard, booking form, approvals) judged against `DESIGN.md` for alignment, spacing, contrast and overflow; fix what fails, then run the impeccable audit if it is installed.

## Phase 7 — Ship-ready
**Agents:** backend-dev; reviewer.
**Work:** multi-stage `Dockerfile` for the backend; `.github/workflows/ci.yml` (BE verify with Testcontainers, FE build + test, E2E with a Postgres service); `.env.example`; `vercel.json` with SPA rewrites; `docs/DEPLOY.md` (≤ 40 lines: Neon env vars, Render, Vercel, CORS, `JWT_SECRET`, seeding off in prod); `docs/MANUAL_QA.md` (≤ 20 lines: real-phone checklist for me). **Do not deploy and do not push.** Cleanup: code-simplifier over the whole diff; remove dead code and unused dependencies; grep for `TODO`, `FIXME`, `console.log`, `System.out`, `@SuppressWarnings`, `any`, hard-coded secrets; reduce `ponytail:` markers to real trade-offs.
**Gate:**
- Fresh-clone check: `git clone . ../sb-verify` then BE verify, `npm ci`, FE build and FE test pass there (proves lockfiles and committed files are complete).
- Docker image builds, runs against the compose Postgres, and `smoke.mjs` passes against the container.
- `reviewer` writes `docs/REVIEW.md` (≤ 300 words): every spec requirement traced to code and tests, the 80% coverage numbers, remaining risks.
- Final commit; then tell me what is left for me to do by hand (deploy, real-phone test).
