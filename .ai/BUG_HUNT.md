# Dual-AI Bug Hunt Workflow

Use this procedure whenever the user asks to find bugs, audit the app, hunt regressions, or improve reliability.

## Claude: discovery lead
1. Read `CLAUDE.md`, `.ai/PROJECT.md`, `.ai/ARCHITECTURE.md`, `.ai/BUG_BACKLOG.md`, and relevant source/tests.
2. Inspect the real repository. Do not modify app code during discovery.
3. Prioritize crashes, wrong behavior, data loss, lifecycle/concurrency bugs, networking/offline failures, permissions, memory/resource leaks, security/privacy, accessibility, performance, and Android/API compatibility.
4. For ErdCal also inspect: Calendar Provider read/write correctness (`CalendarContract` Events/Instances/Reminders), timezone and all-day-event handling, repeating-event (RRULE) edge cases, the AlarmManager notification stack (scheduling, snooze, reboot survival via `BootReceiver`, Doze/App Standby interaction), the full-screen event-alert Activity's wake/lock-screen-bypass behavior, Calendar Provider permission loss/revocation handling, behavior with no calendars synced yet (DAVx5 absent or not yet configured), Month/Day/Agenda view date-math correctness, search correctness, DataStore preferences persistence across process death, and e-ink jump-scroll/redraw behavior on API 28+.
5. Record credible findings in `.ai/BUG_BACKLOG.md` with severity, confidence, file/component, evidence, reproduction steps, expected/current behavior, proposed regression test, and runtime verification needed.
6. Do not report style preferences as bugs.
7. Select exactly one highest-value bug that can be safely fixed in isolation and write its complete contract to `.ai/CURRENT_TASK.md`.
8. End discovery with `READY FOR CODEX FEASIBILITY AND REPRODUCTION`.

## Codex: reproduce, fix, verify
1. Read `AGENTS.md`, `.ai/PROJECT.md`, `.ai/ARCHITECTURE.md`, `.ai/CURRENT_TASK.md`, and this file.
2. Independently inspect the repository before trusting Claude's diagnosis.
3. Attempt to reproduce or otherwise prove the bug before editing when practical.
4. If diagnosis is wrong/incomplete, write `DESIGN REVISION REQUIRED` to `.ai/VERIFICATION.md` with evidence and do not make a speculative fix.
5. If confirmed, identify root cause, implement the smallest coherent fix, and add a regression test whenever practical.
6. Run applicable Gradle build/tests/lint and emulator/device verification when available.
7. Verify the original failure before and success after when practical.
8. Inspect `git diff` for unrelated changes.
9. Record reproduction, root cause, files changed, regression test, exact commands/results, build/runtime results, residual risk, and contract deviations in `.ai/VERIFICATION.md`.
10. End with `READY FOR CLAUDE ADVERSARIAL REVIEW`.

## Claude: adversarial review
Review actual diff/evidence/tests and confirm root cause was fixed rather than masked. Actively seek one remaining failure mode, one regression, one uncovered edge case, one simpler/safer implementation, and one missing test. For significant fixes construct at least three adversarial scenarios.

If corrections are required, return actionable severity/file/problem/required behavior/verification items for Codex. If complete, end exactly with `VERIFICATION PASSED: READY TO COMMIT`.

## Loop discipline
Fix one bug at a time: discover/rank -> select one -> Codex reproduce/fix -> Claude review -> commit -> select next. Preserve unresolved findings in `.ai/BUG_BACKLOG.md`.
