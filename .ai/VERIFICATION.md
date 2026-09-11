# Verification Record — BUG-003

Status: `COMPLETE`

Codex owns execution evidence here; Claude reviews it against the repo and task contract.

## Feasibility Review

Result: `DESIGN FEASIBLE`

Independent inspection confirmed Gradle `9.0-milestone-1`, AGP/lint `8.3.0`,
Kotlin `1.9.22`, Compose UI `1.7.3`, Compose compiler `1.5.10`, and Material3
`1.3.1`. KofC6650 proves AGP 9.4/Kotlin 2.2.10/MMD 1.0.0 is viable, but AGP
`8.7.3` with stable Gradle `8.9` resolves this bug while preserving Kotlin,
the old Compose compiler extension mechanism, dependencies, and Groovy scripts.

## Reproduction

Before editing, `./gradlew lintDebug` completed but its report began with
`ObsoleteLintCustomCheck`, named the missing
`UastLintUtilsKt.isIncorrectImplicitReturnInLambda(UElement)` API, and skipped
ten Compose UI checks. Baseline: 0 errors and 27 warnings.

## Root Cause

AGP 8.3.0 supplied a lint engine older than the API used to compile Compose UI
1.7.3's bundled `lint.jar`. Updating AGP and its wrapper together resolves the
binary/API mismatch.

## Files Changed

- `build.gradle`: AGP `8.3.0` -> `8.7.3`.
- `gradle/wrapper/gradle-wrapper.properties`: Gradle `9.0-milestone-1` -> `8.9`.
- `.ai/VERIFICATION.md`: this evidence.

No app source, SDK/dependency version, lint configuration, or backlog entry
changed. The restored checks produced no new findings.

## Regression Test

No source test is practical for a lint registry binary-compatibility failure.
The direct regression is real `lintDebug` execution and report inspection: the
obsolete warning is absent and HTML metadata contains all ten formerly skipped
IDs. `testDebugUnitTest` remains `NO-SOURCE` as expected.

## Commands Executed

Every Gradle command first exported the required Java 17 `JAVA_HOME`.

```bash
./gradlew lintDebug # before and after
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug
./gradlew :app:dependencyInsight --dependency com.mudita:MMD --configuration debugRuntimeClasspath
./gradlew signingReport
adb devices -l
adb install -r app/build/outputs/apk/release/ErdCal-1.3.4.apk
adb shell uiautomator dump ...
adb shell dumpsys alarm
adb logcat -d -t 500
git diff --check
```

## Results

- Matrix: Gradle `9.0-milestone-1` -> `8.9`; AGP `8.3.0` -> `8.7.3`;
  Kotlin/Compose UI/compiler/Material3 remain `1.9.22`/`1.7.3`/`1.5.10`/`1.3.1`.
- Build/tests: PASS. Debug and signed release assembled in the combined 100-task
  run; `signingReport` confirmed the existing release configuration;
  `testDebugUnitTest NO-SOURCE` was expected.
- Lint: PASS. No `ObsoleteLintCustomCheck`; HTML metadata includes all ten prior
  check IDs. Result remains 0 errors/27 warnings, so there are no new Compose
  findings to backlog. BUG-004's `BatteryLife` warnings remain untouched.
- Dependencies: PASS. MMD and MMD-android 1.0.0 resolve on the AGP 8.7.3
  runtime classpath; compile, package, install, and rendering succeeded.
- Device: PASS on Mudita Kompakt `MK20250402537`. The signed release rendered
  Agenda, Month, Day, Search, Settings, Add Event, and Event Detail/Edit with
  working MMD controls/navigation. No recent ErdCal fatal exception was logged.
- BUG-001: PASS. Changing a temporary event reminder from 5 to 10 minutes
  removed 12:55 and left exactly 12:50 plus the 13:00 event-start alarm.
  Deleting the test event removed both alarms.

## Residual Risk

No API 28 emulator, reboot/Doze cycle, or actual alarm-delivery wait was run.
The device test covered Compose/MMD navigation and alarm rescheduling, while
debug/release compilation covered the full app.

## Contract Deviations

None. AGP 9/Kotlin 2 was intentionally not used because the smaller stable
AGP 8.7.3/Gradle 8.9 pair directly fixed the proven issue.

READY FOR CLAUDE ADVERSARIAL REVIEW
