---
paths:
  - "backend/src/test/**"
  - "frontend/**/*.spec.ts"
---
# Testing rules
- Write tests from `docs/contract/` (domain.md, test-plan.md, openapi.yaml), not from the implementation.
- Test names match the planned names in `docs/contract/test-plan.md`.
- Real Postgres via Testcontainers. Never H2.
- Never mock the unit under test.
- No `sleep`. Time is controlled through the injected `Clock`.
- Overlap tests are `@ParameterizedTest` covering: identical, contained, containing, overlap on each side, adjacent before, adjacent after, 1-minute overlap, different room, ignored statuses (CANCELLED, NO_SHOW, COMPLETED).
- Authorization tests are generated from the permission matrix in `domain.md`: each endpoint × role (none, EMPLOYEE, MANAGER, ADMIN) plus ownership (IDOR) cases.
- Error assertions check HTTP status and ProblemDetail `code`.
- Never weaken an assertion to pass. A contract contradiction is reported, not patched.
