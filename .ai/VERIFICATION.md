# Verification Record — BUG-004

Status: `COMPLETE`

Codex owns execution evidence here; Claude reviews it against the repo and task contract.

## Feasibility Review

Result: `DESIGN FEASIBLE`

Independent source inspection found exactly three direct `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent constructions at the contracted sites and no existing suppressions. The generated lint report attributed exactly three `BatteryLife` warnings to those expressions. A line-local `//noinspection BatteryLife` directive is accepted by lint 8.7.3, so declaration-wide `@SuppressLint` annotations are unnecessary.

## Reproduction

Before editing, a Java 17 `./gradlew lintDebug` run succeeded and generated 0 errors/27 warnings. `lint-results-debug.txt` identified `BatteryLife` at `CalendarPermissionGate.kt:107`, `OnboardingScreen.kt:244`, and `SettingsScreen.kt:260`; no other `BatteryLife` findings existed.

## Root Cause

The three intentional direct exemption requests trigger the Android lint Play-distribution policy check. ErdCal needs exemption for reliable alarm delivery on Doze-prone hardware and is a personal-use app not distributed through Google Play, so each finding is intentional but previously undocumented and unsuppressed.

## Files Changed

- `CalendarPermissionGate.kt`, `OnboardingScreen.kt`, and `SettingsScreen.kt`: added a justification comment and line-local `//noinspection BatteryLife` immediately before each direct request intent. Intent actions, package URIs, guards, and activity launches remain unchanged.
- `.ai/VERIFICATION.md`: recorded BUG-004 evidence and results.

## Regression Test

No source-level automated test is appropriate for comment-only lint suppression. The direct regression check is a real `lintDebug` run plus report inspection: the result changed from 0 errors/27 warnings to 0 errors/24 warnings, with all three and only the three `BatteryLife` findings removed. The final issue inventory is 2 `AutoboxingStateCreation`, 14 `GradleDependency`, 2 `MonochromeLauncherIcon`, 5 `ObsoleteSdkInt`, 1 `OldTargetApi`, 1 `RedundantLabel`, and 1 `UseOfNonLambdaOffsetOverload`.

## Commands Executed

Every Gradle command first exported `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` as `JAVA_HOME`.

```bash
./gradlew lintDebug                         # pre-edit baseline
./gradlew lintDebug                         # corrected post-edit verification
./gradlew assembleDebug assembleRelease
./gradlew testDebugUnitTest
ruby -r rexml/document -e ... app/build/reports/lint-results-debug.xml
git diff --check
git diff -- app/src/main/java/com/kompakt/calendar/...
git status --short
```

An initial post-edit lint invocation overlapped a diff check that caught a temporary replacement-tool interpolation in the onboarding package URI. The URI was restored to the exact original `package:${context.packageName}` before the accepted lint/build/test runs; final diff inspection confirms no runtime expression changed.

## Results

- Build: PASS. `assembleDebug` and `assembleRelease` completed successfully in one 88-task run.
- Tests: PASS. `testDebugUnitTest` completed successfully with the expected `NO-SOURCE` result.
- Lint: PASS. Final report is 0 errors/24 warnings and contains no `BatteryLife` issue. The remaining 24 issue identities/counts match the pre-edit baseline after subtracting the three targeted findings.
- Diff hygiene: PASS. `git diff --check` reported no whitespace errors; the app-source diff contains only six explanatory/suppression comment lines.
- Device/runtime: Not run; the contract does not require it for comment-only lint suppressions, and final diff inspection proves the request actions, data URIs, guards, and launches are byte-for-byte unchanged.

## Residual Risk

A future lint version could stop recognizing line-local `//noinspection` directives; the adjacent justification comments make the intent clear if that occurs. No API 28/device interaction was exercised because executable behavior did not change.

## Contract Deviations

None. The narrow line-local suppression avoided the broader enclosing-Composable scope called out in the contract.

READY FOR CLAUDE ADVERSARIAL REVIEW
