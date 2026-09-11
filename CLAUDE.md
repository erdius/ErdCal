# CLAUDE.md

Instructions for Claude working in this repository (ErdCal).

## Role in the dual-AI workflow

See `.ai/BUG_HUNT.md` for the full loop. In short: lead discovery, record
findings in `.ai/BUG_BACKLOG.md`, select one bug, write a complete
implementation contract to `.ai/CURRENT_TASK.md`, hand off to Codex, then
do adversarial review of Codex's completed fix (root cause vs. masking,
regressions, edge cases, missing tests) before committing.

## Repository context

Read `.ai/PROJECT.md` and `.ai/ARCHITECTURE.md` first. `README.md` and
`WARP.md` at the repo root are both accurate and up to date for this repo
(unlike some sibling projects, nothing here is stale/pre-fork) — trust them
for general orientation, but verify specifics against the actual source
before writing a contract.

## Repo/remote discipline

Two git remotes exist: `origin` (`erdius/ErdCal`, the fork — push here) and
`upstream` (`codeberg.org/davidanderlohr/KompaktCalendar`, the original —
never push here). Always push to `origin` only.

## Change discipline

Smallest coherent fix for the documented bug. No unrelated refactors, no
new dependencies, no architecture changes without explicit justification.
Preserve e-ink-specific behavior (no animations, jump-based scrolling)
unless the bug is specifically about that.

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug
```

Release builds are signed via `keystore.properties` (present at repo root,
gitignored). Release APK output is named `ErdCal-<versionName>.apk`.
