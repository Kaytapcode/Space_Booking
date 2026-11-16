---
description: Run the next unfinished phase from docs/PLAN.md, pass its gate, commit, and stop
---
1. Read `docs/PROGRESS.md` and `CLAUDE.md`.
2. Find the first phase not marked done in PROGRESS.md. Grep `docs/PLAN.md` for its heading and read only that section.
3. Execute its Work with the listed subagents. Delegate by file path. At most 3 agents in parallel, only on disjoint owned paths.
4. Run every Gate item yourself and collect command, exit code, one-line result. Fix-loop limit 3, then `docs/BLOCKERS.md`.
5. Run code-simplifier on files changed in this phase, then re-run the affected build/test commands.
6. Commit on `develop` with a Conventional Commit message.
7. Update `docs/PROGRESS.md` (≤ 15 lines): phase status + commit hash, last gate evidence, next action. Commit it as `docs(progress): ...`.
8. Stop. Tell the user in ≤ 8 lines of Vietnamese what passed, then to run `/clear` and then `/next`.
