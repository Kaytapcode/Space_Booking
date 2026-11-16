---
paths:
  - "e2e/**"
  - "scripts/smoke.mjs"
---
# E2E rules
- The action under test goes through the UI with real clicks and typing.
- `page.request` is for setup and verification only.
- Locators: `getByRole`, `getByLabel`, `getByText`. Never CSS or XPath.
- No `waitForTimeout`. `retries: 0`.
- After each UI action, assert the visible result, then verify the final state through the API.
- Time is moved only via `POST /test-support/clock`. Test-support endpoints exist only under `@Profile("e2e")`.
- A flaky test gets a root-cause fix, never a retry.
