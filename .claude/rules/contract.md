---
paths:
  - "docs/contract/**"
---
# Contract rules
- `docs/contract/**` is frozen once `docs/contract/.frozen` exists (enforced by `.claude/hooks/guard.js`).
- A change needs explicit user approval first.
- Then update `schema.sql`, `openapi.yaml`, `domain.md`, `test-plan.md` (and `DECISIONS.md`) together in one commit, plus `backend/**/V*__*.sql` and the diagrams if affected.
- No "TBD". Every error `code` used must be in the OpenAPI catalog.
