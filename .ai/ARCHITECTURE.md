# ErdCal Architecture Guidance

Preserve existing repository patterns unless a task explicitly requires
architectural change.

## Boundaries to protect

- Compose UI (`CalendarScreen`, `DayViewScreen`, `AgendaScreen`,
  `AddEventScreen`, `EventDetailScreen`, `SettingsScreen`,
  `EventSearchScreen`, `NotesScreen`) consumes `CalendarViewModel` state via
  StateFlow — it should not own Calendar Provider access or alarm
  scheduling directly.
- All calendar data reads/writes go through `CalendarRepository` and the
  Android `CalendarContract` ContentProvider — there is deliberately no
  local database. Do not introduce one as a side effect of an unrelated
  fix.
- Notification scheduling (`NotificationScheduler`, `NotificationReceiver`,
  `BootReceiver`) is a separate stack from Calendar Provider alerts — do
  not conflate the two or assume `CalendarContract`'s own reminder
  mechanism is in play.
- The full-screen event-alert Activity's lock-screen-bypass/wake behavior
  is intentional and load-bearing (that's the whole point of the feature)
  — do not treat it as a bug unless the task specifically says so.
- E-ink UI discipline: no animations, jump-based scrolling only (matches
  Mudita MMD's `LazyColumnMMD` pattern), high-contrast monochrome. Don't
  reintroduce smooth-scroll/animated transitions as a side effect of an
  unrelated fix.

## Android/mobile checks

For relevant changes verify: process death/recreation, alarm survival
across reboot (`BootReceiver`) and across Doze/App Standby, timezone and
DST correctness for scheduled alarms, Calendar Provider permission loss
(previously-granted calendar access revoked), behavior when DAVx5 or any
sync adapter is absent/not yet synced, all-day vs. timed event edge cases,
repeating-event (RRULE) edge cases, and API 28 behavior.

## Change discipline

Prefer small diffs. Do not introduce abstraction, dependencies, or broad
refactors without a concrete need. Shared/public contracts require
explicit migration and regression analysis. Do not reintroduce upstream
KompaktCalendar functionality that was deliberately changed in this fork
as a side effect of an unrelated fix.
