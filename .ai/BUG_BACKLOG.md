# Bug Backlog

Track credible bugs here. Keep `.ai/CURRENT_TASK.md` limited to the one bug currently being worked.

## Status values
- NEW
- CONFIRMED
- IN_PROGRESS
- FIXED_PENDING_REVIEW
- VERIFIED
- REJECTED

## Finding template
### BUG-XXX — Short title
- Status: NEW
- Severity: Critical / High / Medium / Low
- Confidence: High / Medium / Low
- Area:
- File/component:
- Evidence:
- Reproduction steps:
- Expected behavior:
- Current behavior:
- Proposed regression test:
- Runtime verification needed:
- Notes:

## Findings

### BUG-001 — Editing an event's reminder offset leaves the old reminder alarm scheduled (stale/duplicate notification)
- Status: CONFIRMED (selected as current task)
- Severity: High
- Confidence: High
- Area: AlarmManager notification stack, event edit flow
- File/component: `app/src/main/java/com/kompakt/calendar/CalendarViewModel.kt`'s `saveEvent()` edit branch (~lines 395-409); `app/src/main/java/com/kompakt/calendar/NotificationScheduler.kt`'s `cancelEventNotifications()` (~lines 144-169), which re-queries `CalendarContract.Reminders` for the event instead of being told which reminders were actually scheduled.
- Evidence:
  - `CalendarRepository.updateEvent()` (`calendar/CalendarRepository.kt` ~lines 438-456) deletes ALL existing `Reminders` rows for the event and inserts new ones matching the just-edited `event.reminders`, as part of the single `repo.updateEvent(editId, ne)` call.
  - `CalendarViewModel.saveEvent()`'s edit branch calls `repo.updateEvent(editId, ne)` FIRST (line 399), and only AFTER it returns `ok = true` does it call `NotificationScheduler.cancelEventNotifications(getApplication(), editId)` (line 402) — by which point the Reminders table already reflects the NEW values, not the old ones.
  - `cancelEventNotifications()` cancels by re-querying `CalendarContract.Reminders` for `eventId` (via the private `queryReminders()`) and looking up a `PendingIntent` keyed by `pendingIntentId(eventId, isReminder=true, minutes)` for each minute value found — FLAG_NO_CREATE, so a lookup for a minutes-value that was never actually scheduled (because it's the NEW value, not the OLD one that's actually pending) returns null and nothing is cancelled.
  - Net effect: editing an event's reminder from e.g. "10 minutes before" to "30 minutes before" schedules the new 30-minute alarm correctly, but the original 10-minute alarm is never found/cancelled and still fires at the old time. Turning reminders off entirely during an edit (empty list) is the worst case — `queryReminders()` then returns nothing to iterate, so NO previously-scheduled reminder alarm is ever cancelled, and it fires despite the user having just disabled it.
  - By contrast, `deleteCurrentEvent()`/`deleteEventById()` (same file, ~lines 423-441) call `cancelEventNotifications()` BEFORE `repo.deleteEvent(...)`, so that path correctly queries reminders while they still exist and isn't affected. `moveEvent()` (~lines 336-362) has the same before/after ordering as the edit path but isn't affected in practice because it passes `event.reminders` unchanged (a move doesn't alter reminder minutes), so the re-queried values happen to still match what's scheduled.
- Reproduction steps:
  1. Create an event at least 15 minutes in the future with a 10-minute-before reminder.
  2. Edit the event and change its reminder to 5-minutes-before (or remove the reminder entirely), save.
  3. Wait until 10 minutes before the original start time (or use `adb shell dumpsys alarm | grep -A3 kompakt.calendar` to inspect scheduled alarms instead of waiting).
  4. Observe: the old 10-minute-before alarm is still scheduled/fires, in addition to (or instead of the absence of) the new one.
- Expected behavior: Editing or removing an event's reminder should cancel every previously-scheduled alarm for that event's old reminder configuration before scheduling the new one.
- Current behavior: Stale alarms from before the edit remain scheduled whenever the edit changes which reminder-minute values are set.
- Proposed regression test: No test infrastructure exists in this project yet (no `app/src/test`/`app/src/androidTest` sources present). A reviewable diff-level check: confirm the old reminders are captured and used for cancellation before `repo.updateEvent`/`updateEventInstance` overwrites the Reminders table.
- Runtime verification needed: `adb shell dumpsys alarm | grep -A5 com.erdman.erdcal` before and after an edit that changes reminder minutes, confirming the old alarm is gone and only the new one remains.
- Notes: Selected as the current task — concrete, reproducible, root-caused precisely to a call-ordering issue, matches `.ai/BUG_HUNT.md`'s "AlarmManager notification stack" and "lifecycle" focus areas.
