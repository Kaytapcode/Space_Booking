# SpaceBooking
Internal web app for booking meeting rooms by time slot without conflicts (roles EMPLOYEE, MANAGER, ADMIN).
Read `docs/PROGRESS.md` first. Spec (Vietnamese, source of truth): `docs/SpaceBooking.md`. Phase plan: `docs/PLAN.md`.

## Stack
- Backend: Java 21, Spring Boot 3.3+ (Web, Data JPA, Security, Validation), PostgreSQL 16, Caffeine, Lombok, JWT HS256, Flyway, springdoc (conformance check only).
- Backend tests: JUnit 5, Mockito, Testcontainers, JaCoCo.
- Frontend: Angular 17+ standalone, TailwindCSS, openapi-typescript.
- E2E: @playwright/test, @axe-core/playwright. Contract lint: @redocly/cli.
- Host: Windows + PowerShell. No bash-only syntax. `JAVA_HOME` must point to JDK 21.

## Map
- `docs/contract/`: frozen contract (schema.sql, openapi.yaml, domain.md, test-plan.md, DECISIONS.md)
- `docs/design/DESIGN.md`: design tokens. `docs/diagrams/`: Mermaid. `docs/BLOCKERS.md`, `docs/notes/api-notes.md`
- `backend/`: Spring Boot, package-by-feature `com.spacebooking.<feature>`
- `frontend/`: Angular. `e2e/`: Playwright. `scripts/smoke.mjs`: black-box HTTP smoke

## Commands (run from repo root)
| Alias | Command |
|---|---|
| Contract lint | `npx @redocly/cli lint docs/contract/openapi.yaml` |
| DB up | `docker compose up -d postgres` |
| BE verify | `.\backend\mvnw.cmd -q -f backend\pom.xml verify` |
| BE one test | `.\backend\mvnw.cmd -q -f backend\pom.xml test '-Dtest=<Class>'` |
| FE build | `npm --prefix frontend run build '--' --configuration production` |
| FE lint | `npm --prefix frontend run lint` |
| FE test | `npm --prefix frontend test '--' --watch=false` |
| E2E | `npm --prefix e2e test` |
| Smoke | `node scripts/smoke.mjs` |

## Rules
1. Evidence over claims. Never say done/passing/works without a command run this session. Report command, exit code, one-line result. A phase is done only when every gate command passes.
2. Never weaken a check: no skipping, deleting or loosening tests, thresholds, lint rules or assertions. Fix the code. If a test contradicts the contract, stop and ask.
3. Contract-first. `docs/contract/**` is frozen after GATE 1. A change needs explicit user approval, then schema, OpenAPI, domain.md and test-plan.md change together in one commit.
4. No stack creep. Only the stack above. Anything else goes in `docs/contract/DECISIONS.md` and needs approval.
5. Token discipline. Delegate by file path, never paste contents. Grep first, Read with offset/limit. Never re-read a file you just wrote. Run builds quietly (`-q`, `--silent`) and report failing lines only. Subagent reports ≤ 150 words, detail goes in files. No narration, no recaps.
6. Fix-loop limit. Max 3 fix cycles per failing gate item, then write the root cause to `docs/BLOCKERS.md`, continue independent work, report.
7. Git. Commit locally on `develop` after each green gate (Conventional Commits). Never push, force, `reset --hard` or `git clean`. Never commit secrets or `.env`.
8. Autonomy. After GATE 1 ask no questions. Decide from the contract, record it in `docs/contract/DECISIONS.md`, continue. Stop only for a contract change, a destructive action outside build output, or a missing credential.
9. Plugins. LSP diagnostics first; full builds only at slice end and at gates. context7 only when unsure of an exact API signature (1 lookup per question, save to `docs/notes/api-notes.md`, ≤ 40 lines). frontend-design and ui-ux-pro-max once, in the design phase. playwright plugin: accessibility snapshots over images, ≤ 4 screenshots per iteration. code-simplifier at phase end on changed files only. ponytail in `lite` mode.
10. ponytail never touches State, Strategy, Specification, Observer, tests, validation, security, error handling or accessibility. Keep `ponytail:`/`shortcut:` markers only for real trade-offs.
- Talk to the user in Vietnamese. Code, identifiers, comments, commits, `.claude/`, `docs/contract/`, `docs/PLAN.md` in English. UI copy in Vietnamese.
