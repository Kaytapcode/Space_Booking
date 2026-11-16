---
paths:
  - "frontend/**"
---
# Frontend rules
- Angular standalone components, signals, `@if`/`@for`, functional interceptors and guards, reactive forms.
- API types come from the `openapi-typescript` output. Never hand-write DTO interfaces.
- Styling uses only the tokens in `docs/design/DESIGN.md`.
- Every list has loading, empty and error states.
- Every input has a label. Visible focus ring. Contrast ≥ 4.5:1. Touch targets ≥ 44px on mobile.
- One primary colour plus a neutral scale.
- Fixed status colours: PENDING amber, CONFIRMED blue, CHECKED_IN green, CANCELLED/NO_SHOW gray, COMPLETED slate.
- No purple or blue-purple gradients, no glassmorphism, no emoji as icons, no hero or marketing layouts (dense internal tool).
- One icon library. Radius ≤ 8px. Shadow at most `sm`. 4px spacing scale.
- `tabular-nums` for times. Font with a Vietnamese subset.
- Display all times in `Asia/Ho_Chi_Minh`; send UTC ISO-8601 to the API.
- Vietnamese copy is short and says what to do next. No exclamation marks.
- Error toasts map ProblemDetail `code` to a Vietnamese message; never show raw `detail` from the server.
