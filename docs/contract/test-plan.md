# SpaceBooking — Test Plan (traceability)
Levels: **U** unit (JUnit 5 + Mockito, fixed `Clock`), **I** integration (Spring Boot + Testcontainers Postgres 16), **E** e2e (Playwright through the UI).
Refs point to `domain.md` sections. Coverage gate: JaCoCo line >= 80% on `**/service/**` and `**/domain/**`.

## Domain rules
| ID | Rule (ref) | Level | Planned test |
|---|---|---|---|
| R01 | Overlap predicate, all edge cases (§2) | U | `OverlapPolicyTest.overlaps(@ParameterizedTest)`: identical, contained, containing, overlap left, overlap right, adjacent before, adjacent after, 1-minute overlap |
| R02 | Different room never conflicts (§2) | I | `BookingRepositoryIT.existsOverlap_differentRoom_false` |
| R03 | Ignored statuses do not block (§2) | I | `BookingRepositoryIT.existsOverlap_ignoresStatus(@ParameterizedTest CANCELLED, NO_SHOW, COMPLETED)` |
| R04 | Blocking statuses block (§2) | I | `BookingRepositoryIT.existsOverlap_blocks(@ParameterizedTest PENDING, CONFIRMED, CHECKED_IN)` |
| R05 | Employee max 120 min inclusive (§3) | U | `EmployeePolicyTest.duration(@ParameterizedTest 120 ok, 121 DURATION_EXCEEDED)` |
| R06 | Employee status by requiresApproval (§3) | U | `EmployeePolicyTest.initialStatus_pendingWhenRequiresApproval`, `..._confirmedOtherwise` |
| R07 | Manager unlimited, always CONFIRMED (§3) | U | `ManagerPolicyTest.longBooking_confirmed`, `..._approvalRoom_confirmed` |
| R08 | Policy chosen by role; ADMIN rejected (§3) | U | `BookingPolicyResolverTest.resolves(@ParameterizedTest)`, `..._admin_accessDenied` |
| R09 | Whole-minute times (§1) | U | `BookingRequestValidationTest.secondsNotZero_rejected` |
| R10 | `end > start` (§1) | U | `BookingRequestValidationTest.endNotAfterStart_rejected` |
| R11 | Start before current minute rejected (§1) | U | `BookingServiceTest.create_startInPast_startInPast`, `..._currentMinute_ok` |
| R12 | Valid transitions (§4) | U | `BookingStateTest.validTransition(@ParameterizedTest one row per §4 transition)` |
| R13 | Every other pair -> INVALID_STATE_TRANSITION (§4) | U | `BookingStateTest.invalidTransition(@ParameterizedTest all remaining status x action pairs)` |
| R14 | Approve/reject/cancel after start -> BOOKING_ALREADY_STARTED (§4) | U | `BookingStateTest.afterStart_alreadyStarted(@ParameterizedTest approve, reject, cancel)` |
| R15 | Check-in window inclusive ±15 min (§4) | U | `ConfirmedStateTest.checkIn(@ParameterizedTest -16 fail, -15 ok, 0 ok, +15 ok, +16 fail)` |
| R16 | Reject stores reason (§4) | U | `PendingStateTest.reject_setsReason` |
| R17 | Early checkout end rule (§6) | U | `CheckedInStateTest.checkout(@ParameterizedTest mid-slot rounds up, before start -> start+1m, after end keeps end)` |
| R18 | Freed tail is bookable after early checkout (§6) | I | `BookingFlowIT.earlyCheckout_freesRemainingSlot` |
| R19 | Scheduler expires PENDING at start (§5) | U | `BookingSchedulerTest.expiresPendingAtStart` |
| R20 | Scheduler NO_SHOW after start+15m, not at +15m (§5) | U | `BookingSchedulerTest.noShow(@ParameterizedTest +15 stays, +16 no-show)` |
| R21 | Scheduler completes CHECKED_IN at end (§5) | U | `BookingSchedulerTest.completesAtEnd` |
| R22 | Scheduler: one failure does not stop the run (§5) | U | `BookingSchedulerTest.continuesAfterFailure` |
| R23 | Scheduler writes audit as SYSTEM_SCHEDULER and evicts cache (§5, §8) | I | `BookingSchedulerIT.transition_writesAuditAndEvicts` |
| R24 | Concurrent creation: exactly one wins (§7) | I | `BookingConcurrencyIT.parallelCreate_oneCreated_restConflict` (N threads, CountDownLatch, same slot) |
| R25 | Optimistic failure -> 409 BOOKING_CONFLICT (§7) | I | `BookingConcurrencyIT.optimisticLockFailure_mapsTo409` |
| R26 | Exclusion constraint backstop -> 409 (§7) | I | `BookingRepositoryIT.exclusionConstraint_rejectsOverlap`, `GlobalExceptionHandlerIT.exclusionViolation_409` |
| R27 | Deactivation races creation (§7) | I | `RoomConcurrencyIT.deactivateVsCreate_noBookingOnInactiveRoom` |
| R28 | Room optimistic failure -> CONCURRENT_UPDATE (§7) | I | `RoomControllerIT.update_concurrent_409ConcurrentUpdate` |
| R29 | Audit row per transition, same transaction (§8) | I | `AuditListenerIT.transition_writesOneRow(@ParameterizedTest each action)`, `..._rollbackLeavesNoRow` |
| R30 | Audit details content (§8) | U | `AuditLogListenerTest.details_includesReason_and_endTime` |
| R31 | Cache hit then eviction after commit (§8, §10) | I | `DailyScheduleCacheIT.secondCall_hitsCache`, `..._evictedAfter(@ParameterizedTest create, approve, reject, cancel, checkIn, checkout, expire, noShow, complete, roomCreate, roomPut, roomPutActiveFalse, roomDelete)` |
| R32 | Warm daily-schedule < 50 ms (§10) | I | `DailyScheduleCacheIT.warmResponse_under50ms` |
| R33 | No N+1 on schedule and lists (§10) | I | `QueryCountIT.dailySchedule_constantQueries`, `..._myBookings_constantQueries` (Hibernate statistics) |
| R34 | Day boundaries in Asia/Ho_Chi_Minh (§1) | I | `DailyScheduleIT.bookingAt2330Local_onlyThatDay`, `..._spanningMidnight_bothDays` |
| R35 | Schedule shows only blocking bookings, no email (§10, G11) | I | `DailyScheduleIT.excludesNonBlocking_andHidesEmail` |
| R36 | Equipment whole-token AND filter (§11) | I | `RoomSpecificationIT.equipments(@ParameterizedTest tv excludes tvbox, tv+whiteboard requires both)` |
| R37 | Capacity and availability filters (§11) | I | `RoomSpecificationIT.minCapacity`, `..._availability_excludesBlocked`, `..._availability_adjacentIncluded` |
| R38 | Availability params must come together (§11) | I | `RoomControllerIT.search_onlyAvailableFrom_400` |
| R39 | Inactive room hidden/unbookable for non-admin (§11) | I | `RoomControllerIT.getInactive_employee_404`, `BookingControllerIT.create_inactiveRoom_404` |
| R40 | Deactivate guard (§11) | U+I | `RoomServiceTest.deactivate_futureBlocking_409`, `RoomControllerIT.delete_withFutureBookings_409`, `..._put_activeFalse_withFutureBookings_409` |
| R41 | Reactivate via PUT active=true (§11, D19) | I | `RoomControllerIT.put_activeTrue_reactivates` |
| R42 | Equipments normalised and sorted (§11) | U | `EquipmentsTest.roundTrip_sortedCommaJoined` |
| R43 | Register creates EMPLOYEE, lower-cases email (§12) | I | `AuthControllerIT.register_createsEmployee_lowercaseEmail` |
| R44 | BCrypt hash stored (§12) | I | `AuthControllerIT.register_storesBcryptHash` |
| R45 | Same 401 for unknown email and wrong password (§12) | I | `AuthControllerIT.login_wrongPassword_and_unknownEmail_sameProblem` |
| R46 | JWT expiry and tampering (§12) | U+I | `JwtServiceTest.expired_rejected`, `..._badSignature_rejected`, `AuthorizationMatrixIT.expiredToken_401` |
| R47 | Missing JWT_SECRET outside dev/e2e fails startup (§12) | I | `JwtSecretStartupIT.prodWithoutSecret_failsToStart` |
| R48 | CORS from env (§12) | I | `CorsIT.allowedOrigin_ok_otherOrigin_rejected` |
| R49 | Seeder gated and idempotent (§13) | I | `DataSeederIT.disabled_seedsNothing`, `..._runTwice_noDuplicates` |
| R50 | Flyway = schema.sql, ddl validate (G14) | I | `SchemaIT.flywayMigrates_andHibernateValidates` |
| R51 | Concurrent transitions on one booking: one wins, one audit row (§7, D28) | I | `BookingTransitionConcurrencyIT.approveVsReject_oneWins`, `..._approveVsCancel_oneWins`, `..._checkInVsNoShow_oneWins`, `..._checkoutVsComplete_oneWins`, `..._cancelVsExpire_oneWins`, `..._lockTimeout_409ConcurrentUpdate` |
| R52 | Scheduler re-checks the locked row and skips (§5) | U | `BookingSchedulerTest.skipsWhenNoLongerEligible` |
| R53 | Scheduler run order expire -> no-show -> complete (§5) | U | `BookingSchedulerTest.runOrder` |
| R54 | Guard order (§4, D29) | I | `GuardOrderIT(@ParameterizedTest)`: no token+bad body 401; EMPLOYEE approve unknown id 403; ADMIN create unknown room 403; MANAGER bad body 400; unknown id 404; non-owner on terminal booking 403; owner on terminal 409; EMPLOYEE `includeInactive=true` + bad `minCapacity` 403 |
| R55 | Input with any offset, responses in UTC `Z` (§1) | I | `BookingControllerIT.create_offsetInput_utcOutput` |
| R56 | Email trimmed, duplicate is case-insensitive (§12) | I | `AuthControllerIT.register_upperCaseDuplicate_409` |
| R57 | `performed_by` is actor email (§8) | I | `AuditListenerIT.performedBy_isActorEmail` |
| R58 | `availableTo <= availableFrom` -> 400 (§11) | I | `RoomControllerIT.search_toNotAfterFrom_400` |
| R59 | Malformed path UUID -> 400 (D31) | I | `GlobalExceptionHandlerIT.malformedUuid_400` |
| R60 | PUT without `active` keeps status (D19) | I | `RoomControllerIT.put_activeOmitted_inactiveStaysInactive` |
| R61 | Sort ties broken by `id` asc (D32) | I | `BookingControllerIT.myBookings_equalStart_orderedById` |
| R62 | Public operations ignore `Authorization` (D33) | I | `AuthControllerIT.login_withExpiredToken_200` |
| R63 | Every error response matches an openapi example code for that operation (D35) | I | `OpenApiConformanceIT.errorCodes_declaredPerOperation` |

## Endpoints (happy path + each listed status code)
| ID | Endpoint | Level | Planned test |
|---|---|---|---|
| A01 | POST /auth/register | I | `AuthControllerIT.register_201`, `..._invalid_400`, `..._duplicate_409` |
| A02 | POST /auth/login | I | `AuthControllerIT.login_200`, `..._invalid_400`, `..._badCredentials_401` |
| A03 | GET /auth/me | I | `AuthControllerIT.me_200`, `..._noToken_401` |
| A04 | GET /rooms | I | `RoomControllerIT.search_200_sortedByName`, `..._badParams_400`, `..._includeInactive_nonAdmin_403` |
| A05 | GET /rooms/{id} | I | `RoomControllerIT.get_200`, `..._unknown_404` |
| A06 | POST /rooms | I | `RoomControllerIT.create_201`, `..._invalid_400`, `..._duplicateName_409` |
| A07 | PUT /rooms/{id} | I | `RoomControllerIT.update_200`, `..._invalid_400`, `..._unknown_404`, `..._duplicateName_409` |
| A08 | DELETE /rooms/{id} | I | `RoomControllerIT.delete_204`, `..._again_204`, `..._unknown_404` |
| A09 | GET /bookings/daily-schedule | I | `DailyScheduleIT.get_200_sortedRoomsAndBookings`, `..._badDate_400` |
| A10 | GET /bookings/my-bookings | I | `BookingControllerIT.myBookings_200_onlyOwn_sortedDesc`, `..._statusFilter`, `..._sizeOver100_400` |
| A11 | GET /bookings/pending | I | `BookingControllerIT.pending_200_sortedAsc`, `..._pendingSizeOver100_400` |
| A12 | POST /bookings | I | `BookingControllerIT.create_201_confirmed`, `..._201_pending`, `..._400`, `..._unknownRoom_404`, `..._overlap_409` |
| A13 | PATCH approve / reject | I | `BookingControllerIT.approve_200`, `..._reject_200`, `..._rejectBlankReason_400`, `..._unknown_404`, `..._notPending_409` |
| A14 | PATCH check-in / checkout / cancel | I | `BookingControllerIT.checkIn_200`, `..._checkout_200`, `..._cancel_200`, `..._outsideWindow_409`, `..._cancelAfterStart_409AlreadyStarted`, `..._unknown_404` |
| A15 | GET /bookings/{id}/logs | I | `AuditControllerIT.logs_200_oldestFirst`, `..._unknown_404` |
| A16 | GET /audit-logs | I | `AuditControllerIT.search_200_filters_newestFirst`, `..._fromAfterTo_400` |
| A17 | Responses match openapi.yaml | I | `OpenApiConformanceIT.springdocSpec_matchesContract` (paths, operations, schema names) |

## Authorization (generated from domain.md §9)
| ID | Rule | Level | Planned test |
|---|---|---|---|
| Z01 | Every endpoint x {none, EMPLOYEE, MANAGER, ADMIN} returns the §9 outcome | I | `AuthorizationMatrixIT.matrix(@ParameterizedTest @MethodSource one row per endpoint x role)` |
| Z02 | IDOR: other EMPLOYEE cannot check-in/checkout/cancel/read logs | I | `AuthorizationMatrixIT.idor(@ParameterizedTest action)` -> 403 `ACCESS_DENIED` |
| Z03 | MANAGER cannot act as owner on others' bookings | I | `AuthorizationMatrixIT.managerNotOwner_checkIn_403` |
| Z04 | my-bookings never leaks other users' bookings | I | `AuthorizationMatrixIT.myBookings_isolation` |
| Z05 | Tampered role claim is rejected | I | `AuthorizationMatrixIT.forgedRoleToken_401` |

## Error codes (catalog in openapi.yaml `ErrorCode`)
| Code | Covered by |
|---|---|
| VALIDATION_FAILED | R09, R10, R58, R59, A01, A02, A04, A13, A16; `GlobalExceptionHandlerIT.validation_hasFieldErrors` |
| START_IN_PAST | R11 |
| DURATION_EXCEEDED | R05, `BookingControllerIT.create_employee121min_400` |
| UNAUTHENTICATED | A03, Z01, R46 |
| INVALID_CREDENTIALS | A02, R45 |
| ACCESS_DENIED | R08, Z01–Z03 |
| ROOM_NOT_FOUND | A05, A12, R39 |
| BOOKING_NOT_FOUND | A13–A15 |
| EMAIL_ALREADY_EXISTS | A01 |
| ROOM_NAME_ALREADY_EXISTS | A06, A07 |
| ROOM_HAS_FUTURE_BOOKINGS | R40 |
| CONCURRENT_UPDATE | R28, R51 |
| BOOKING_CONFLICT | A12, R24–R26 |
| INVALID_STATE_TRANSITION | R13, R51, A13 |
| BOOKING_ALREADY_STARTED | R14, A14 |
| OUTSIDE_CHECKIN_WINDOW | R15, A14 |
| INTERNAL_ERROR | `GlobalExceptionHandlerIT.unexpected_500_noStackTrace` |
| REQUEST_NOT_SUPPORTED | `GlobalExceptionHandlerIT.unknownRoute_404`, `..._wrongMethod_405`, `..._unsupportedMediaType_415`, `..._malformedJson_400ValidationFailed` |

## Frontend unit (Angular)
| ID | Rule | Planned test |
|---|---|---|
| F01 | End after start custom validator | `endAfterStartValidator.spec.ts` |
| F02 | Interceptor adds Bearer and surfaces ProblemDetail `detail` | `auth.interceptor.spec.ts` |
| F03 | Guard blocks routes by role | `role.guard.spec.ts` |
| F04 | Times shown in Asia/Ho_Chi_Minh | `local-time.pipe.spec.ts` |

## E2E (UI actions, API verification)
| ID | Flow | Planned test |
|---|---|---|
| E01 | Login per role lands on the right screen | `auth.spec.ts: login as each seeded role` |
| E02 | Employee books a normal room -> CONFIRMED on dashboard | `booking.spec.ts: employee books normal room` |
| E03 | Employee books approval room -> PENDING; manager approves -> CONFIRMED | `approval.spec.ts: approve flow` |
| E04 | Manager rejects with reason; employee sees reason | `approval.spec.ts: reject flow` |
| E05 | Overlapping booking shows conflict message | `booking.spec.ts: conflict shows ProblemDetail` |
| E06 | Check-in, early checkout, freed slot bookable | `lifecycle.spec.ts: check-in and early checkout` |
| E07 | Cancel own booking | `lifecycle.spec.ts: cancel` |
| E08 | Admin creates, edits, deactivates room; audit table shows actions | `admin.spec.ts: room CRUD and audit` |
| E09 | Mobile < 768px shows cards; desktop >= 1024px shows timeline | `responsive.spec.ts` |
| E10 | No serious axe violations on every screen | `a11y.spec.ts` |
