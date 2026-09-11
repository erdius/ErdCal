# AGENTS.md

Instructions for Codex working in this repository (ErdCal).

## Role in the dual-AI workflow

See `.ai/BUG_HUNT.md` for the full loop. In short: Claude does discovery and
writes a contract to `.ai/CURRENT_TASK.md`. You independently verify the
diagnosis before touching code, implement the smallest coherent fix, run
real builds/tests/lint and device verification when practical, and record
everything in `.ai/VERIFICATION.md`. If Claude's diagnosis is wrong or
incomplete, write `DESIGN REVISION REQUIRED` with evidence instead of
guessing at a fix.

## Repository context

Read `.ai/PROJECT.md` and `.ai/ARCHITECTURE.md` first. `README.md` and
`WARP.md` at the repo root are both accurate and up to date for this repo
(unlike some sibling projects, nothing here is stale/pre-fork) — trust them
for general orientation.

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug
```

Release builds are signed via `keystore.properties` (present at repo root,
gitignored). Release APK output is named `ErdCal-<versionName>.apk`.

## Repo/remote discipline

Two git remotes exist: `origin` (`erdius/ErdCal`, the fork — push here) and
`upstream` (`codeberg.org/davidanderlohr/KompaktCalendar`, the original —
never push here). Always push to `origin` only.

## Change discipline

Smallest coherent fix for the documented bug. No unrelated refactors, no
new dependencies, no architecture changes without explicit contract
authorization. Preserve e-ink-specific behavior (no animations, jump-based
scrolling) unless the bug is specifically about that.
