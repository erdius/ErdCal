# Current Task

Status: `READY FOR CODEX`

Claude owns this file for the active implementation contract.

## Objective

Fix BUG-001: editing an event and changing its reminder offset(s) (or
removing the reminder entirely) leaves the OLD reminder alarm(s) still
scheduled in AlarmManager, because `cancelEventNotifications()` cancels by
re-querying `CalendarContract.Reminders` for the event — but by the time it
runs (after `repo.updateEvent()`/`updateEventInstance()` has already
rewritten that table to the NEW reminder values), it can no longer see what
was actually scheduled before the edit.

## Acceptance Criteria

1. Create an event with a reminder (e.g. 10 minutes before). Edit it to a
   different reminder offset (e.g. 5 minutes before) and save. Verify via
   `adb shell dumpsys alarm | grep -A5 com.erdman.erdcal` (or equivalent)
   that the OLD 10-minute alarm is no longer scheduled and only the new
   5-minute one is.
2. Same, but edit the event to remove its reminder entirely (empty
   reminders list). Verify the old alarm is cancelled and no reminder
   alarm remains scheduled for that event (the event-start, non-reminder
   alarm may still legitimately be rescheduled — that's correct/unrelated).
3. Editing an event WITHOUT changing its reminder configuration must
   continue to work exactly as before (old reminder cancelled and
   identical new one rescheduled — net no-op on the alarm, but must not
   throw or leave duplicates).
4. `moveEvent()`'s existing correct behavior (reminders unchanged during a
   move, cancel/reschedule already works) must be unaffected.
5. `deleteCurrentEvent()`/`deleteEventById()`'s existing correct behavior
   (cancel called before delete, while reminders still exist) must be
   unaffected — do not reorder or touch those call sites.
6. If `repo.updateEvent()`/`updateEventInstance()` returns `false` (update
   failed), no alarms should be cancelled or rescheduled — the failure
   path must remain a no-op on the alarm state, matching current behavior
   (this must NOT regress just because the fix touches nearby ordering).

## Non-Goals

- Do not change `deleteCurrentEvent()`, `deleteEventById()`, or
  `moveEvent()` — their current behavior is already correct (verified in
  the bug backlog entry's evidence). Only `saveEvent()`'s edit branch needs
  a fix.
- Do not change the `PendingIntent` ID scheme
  (`pendingIntentId(eventId, isReminder, reminderMinutes)`) or any other
  part of the scheduling/cancellation mechanism beyond what's needed to
  fix this ordering bug.
- Do not add a test framework/harness to this project as a side effect of
  this fix — no test infrastructure currently exists here (confirmed via
  `find app/src -iname "*Test*"` returning nothing); note in
  `.ai/VERIFICATION.md` why an automated regression test wasn't added,
  matching how other projects in this workspace have handled the same
  constraint.
- Do not touch the "add new event" branch of `saveEvent()` (the `else`
  branch, ~lines 410-420) — there's nothing to cancel for a brand-new
  event, it's unaffected by this bug.

## Repository Findings

- `CalendarViewModel.kt`'s `saveEvent()` edit branch (~lines 393-409):
  ```kotlin
  return if (editId != null) {
      val ok = if (instanceTime != null) {
          repo.updateEventInstance(editId, instanceTime, ne)
      } else {
          repo.updateEvent(editId, ne)
      }
      if (ok) {
          NotificationScheduler.cancelEventNotifications(getApplication(), editId)
          val startMs = ne.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
          val endMs = ne.end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
          NotificationScheduler.scheduleEventNotifications(
              getApplication(), editId, ne.title, startMs, endMs, reminders
          )
      }
      ok
  }
  ```
  The update happens first, THEN cancel is called — but cancel needs the
  PRE-update reminder values to correctly find/cancel the alarms that are
  actually pending.
- `CalendarRepository.kt`'s `updateEvent()` (~lines 441-456) unconditionally
  deletes all `Reminders` rows for the event and re-inserts from
  `event.reminders` as part of the same call — this is what destroys the
  "old" reminder data before `cancelEventNotifications` can see it.
  `updateEventInstance()` (starts ~line 460) needs to be checked for the
  same pattern — confirm whether it also rewrites Reminders, since the
  `instanceTime != null` branch of `saveEvent()` calls it instead of
  `updateEvent()`.
- `NotificationScheduler.kt`'s `queryReminders()` (~lines 72-86) is
  currently `private` — it will need to become accessible from
  `CalendarViewModel` (or `cancelEventNotifications` needs an overload
  that accepts an explicit reminders list) so the OLD reminders can be
  captured BEFORE the update call and passed in for cancellation, rather
  than `cancelEventNotifications` re-querying live DB state itself.
- `cancelEventNotifications()` (~lines 144-169) currently signature:
  `fun cancelEventNotifications(context: Context, eventId: Long)`. Decide
  the smallest correct shape: either (a) add an overload/parameter that
  accepts the old reminders list explicitly and has callers that don't
  have it (delete/move paths) keep using the DB-query version, or (b) have
  `saveEvent()` query old reminders itself (via a newly-exposed
  `NotificationScheduler.queryReminders` or repository equivalent) before
  calling `repo.updateEvent`, then call the existing
  `cancelEventNotifications` — but that still re-queries live state
  internally, which would be wrong after the update already ran. Prefer
  whichever keeps `cancelEventNotifications`'s existing callers
  (delete/move) working unchanged with zero behavior change for them.

## Affected Components

- `app/src/main/java/com/kompakt/calendar/CalendarViewModel.kt` (`saveEvent()`)
- `app/src/main/java/com/kompakt/calendar/NotificationScheduler.kt`
  (`cancelEventNotifications()` and/or `queryReminders()` visibility/shape)

## State / Data Flow

1. Before calling `repo.updateEvent(editId, ne)` / `updateEventInstance(...)`,
   capture the CURRENT (pre-edit) reminder minutes for `editId` — this is
   the last moment they're guaranteed to still match what's actually
   scheduled in AlarmManager.
2. Call the update. If it fails (`ok == false`), do nothing further (no
   cancel, no reschedule) — matching current/required behavior.
3. If it succeeds, cancel using the CAPTURED old reminders (not a fresh
   re-query), then schedule using the NEW `reminders` list exactly as
   today.

## Interface Contracts

Whatever shape is chosen (new parameter, new overload, or exposing
`queryReminders`), it must not change behavior for `deleteCurrentEvent()`,
`deleteEventById()`, or `moveEvent()` — those call sites should either be
left completely untouched, or if a shared helper's signature must change,
updated only to pass equivalent behavior (e.g. explicitly querying current
DB state at their existing call time, which is already correct for them).

## Failure Modes

- `updateEventInstance()` must be checked for the same "rewrites Reminders
  before cancel can see old values" pattern as `updateEvent()` — if it
  doesn't touch Reminders at all (e.g. instance overrides don't carry their
  own reminders), the fix shape may need to differ slightly for that branch.
  Verify against the actual source rather than assuming symmetry with
  `updateEvent()`.
- Ensure the captured "old reminders" query happens on `editId` (the
  master event id) consistently with what `cancelEventNotifications`
  already assumes elsewhere — don't accidentally query by instance id if
  the two differ for recurring events.

## Security / Privacy

Not applicable.

## Compatibility / Migration

No schema/persistence format change (Calendar Provider schema untouched).

## Test Matrix

No test infrastructure exists in this project. Reviewable via diff
inspection: confirm old reminders are captured before the update call and
used (not re-queried post-update) for cancellation.

## Runtime / Device Verification

Required:
1. `adb shell dumpsys alarm | grep -A5 com.erdman.erdcal` (or `com.kompakt.calendar` if that's what shows) before and after each acceptance-criteria scenario above, showing the old alarm is gone and the new one (if any) is present with the correct trigger time.
2. Confirm a normal add-new-event flow and a normal move-event flow are unaffected (existing alarms still get scheduled correctly).
3. Confirm a failed update (if reproducible — e.g. revoke calendar write permission mid-edit, if practical) truly leaves alarm state untouched.

## Codex Handoff
When ready, replace `IDLE` with `READY FOR CODEX`. Codex must perform an
independent feasibility challenge before implementation and record its
evidence in `.ai/VERIFICATION.md`.
