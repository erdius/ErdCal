# Verification Record — BUG-001

Status: `COMPLETE`

## Feasibility Review

Result: `DESIGN FEASIBLE`

Independent source inspection confirmed that `CalendarRepository.updateEvent()` updates the event and then deletes/reinserts all `CalendarContract.Reminders` rows. `CalendarViewModel.saveEvent()` previously called `NotificationScheduler.cancelEventNotifications()` only afterward, and that method queried the now-new reminder rows. The old `PendingIntent` request code therefore could not be reconstructed when an offset changed or was removed.

`updateEventInstance()` does not replace the master event reminders; it inserts a new exception event and reminder rows under the exception ID. The scheduler nevertheless continues to identify the edited alarm by the master `editId`, so taking the master reminder snapshot before either edit path preserves current behavior.

## Reproduction

Before editing code, the faulty ordering was proved statically from the concrete call/data flow above. Runtime reproduction of the unfixed APK was not performed because installing it after the fixed signed APK would add device churn without improving the already-direct proof.

After the fix, a signed release APK was installed on a connected Mudita Kompakt (`MK20250402537`). A uniquely named `BUG001Test` event was created for 2026-09-11 13:00 with a 10-minute reminder. AlarmManager initially showed ErdCal alarms at 12:50 and 13:00.

## Root Cause

Cancellation derived reminder `PendingIntent` IDs from a live post-update Calendar Provider query. When reminder configuration changed, the provider no longer contained the old minute values needed to cancel the already-scheduled old alarms.

## Files Changed

- `app/src/main/java/com/kompakt/calendar/CalendarViewModel.kt`: capture current reminder minutes before either edit update call and, only after a successful update, cancel using that snapshot before scheduling the new configuration.
- `app/src/main/java/com/kompakt/calendar/NotificationScheduler.kt`: retain the existing two-argument cancellation method for move/delete callers and add module-internal query/explicit-reminder cancellation entry points for the edit path.

## Regression Test

No automated test was added. The project has no test sources or existing Android/provider/alarm test harness (`find app/src -iname "*Test*"` returned nothing), and the contract explicitly excludes adding test infrastructure. Regression coverage was performed on the real device plus build and diff inspection.

## Commands Executed

Every Gradle invocation first used:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

Relevant commands:

```bash
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug
./gradlew assembleRelease
./gradlew assembleDebug assembleRelease testDebugUnitTest
./gradlew lintDebug
adb devices -l
adb install -r app/build/outputs/apk/release/ErdCal-1.3.3.apk
adb shell dumpsys alarm
adb shell uiautomator dump /sdcard/erdcal-window.xml
adb shell input tap ...
adb shell input text BUG001Test
git diff --check
git diff -- app/src/main/java/com/kompakt/calendar/CalendarViewModel.kt app/src/main/java/com/kompakt/calendar/NotificationScheduler.kt
```

## Results

- Build: PASS. Final `assembleDebug assembleRelease testDebugUnitTest` completed `BUILD SUCCESSFUL` in 11s. A separate `assembleRelease` also passed.
- Tests: `testDebugUnitTest NO-SOURCE`; there are no test sources.
- Lint: BLOCKED by a pre-existing build configuration defect, before lint analysis: `app/build.gradle` declares `testImplementation junit:junit` without a version, so `generateDebugUnitTestLintModel` fails with `Could not find junit:junit:.`. This task did not modify build dependencies because that is outside BUG-001.
- Diff hygiene: `git diff --check` passed. Only the two contract-listed Kotlin files and this verification record were changed.
- Device add flow: PASS. Creating `BUG001Test` with a 10-minute reminder scheduled exactly one ErdCal reminder alarm at 12:50 and one event-start alarm at 13:00.
- Device change 10→5: PASS. After edit/save, the ErdCal 12:50 alarm was absent; exactly one ErdCal alarm appeared at 12:55 and the 13:00 event-start alarm remained.
- Device remove reminder: PASS. After edit/save with no reminder, ErdCal had no 12:50 or 12:55 alarm; only the 13:00 event-start alarm remained.
- Device unchanged reminder: PASS. The event was restored to a 5-minute reminder, then edited/saved without changing reminder configuration. AlarmManager showed exactly one ErdCal 12:55 reminder and one ErdCal 13:00 event-start alarm (no duplicate).
- Cleanup/delete: PASS. `BUG001Test` was deleted after verification; no ErdCal alarm for its 12:50, 12:55, or 13:00 times remained. Calendar Provider-owned `android.intent.action.EVENT_REMINDER` entries may remain temporarily, but those are outside ErdCal own AlarmManager stack.
- Failed update: not forced at runtime because revoking calendar permission on the users real synced device was unnecessarily disruptive. Static inspection confirms the alarm operations remain inside `if (ok)`, so a `false` result performs no cancellation or rescheduling; the new pre-update operation is read-only.
- Move flow: not manipulated on the users synced calendar. Static inspection confirms `moveEvent()` is byte-for-byte untouched and continues using the existing two-argument cancellation path. Add flow was verified on-device as above.

## Residual Risk

A provider query failure/exception is unchanged in character from the former cancellation path, though it now occurs before the update. Recurring-instance behavior was verified statically but not exercised with a recurring test event on the synced device.

## Contract Deviations

No implementation deviation. Runtime move and failed-update scenarios were not forced because they would require manipulating unrelated synced data or permissions; their required invariants were verified statically. Lint could not complete due the unrelated pre-existing versionless JUnit dependency.

READY FOR CLAUDE ADVERSARIAL REVIEW
