# WARP.md

Guidance for AI assistants (Warp, Claude, etc.) working in this repository.

---

## Project Overview

**ErdCal** is a personal fork of [KompaktCalendar](https://codeberg.org/davidanderlohr/KompaktCalendar), an Android calendar application built with Jetpack Compose, optimised for E-ink displays via the Mudita Mindful Design (MMD) library. It is designed for the **Mudita Kompakt** device but runs on any Android 9+ device.

The app reads and writes events through the **Android Calendar Provider** (`CalendarContract`). It has no local event database. Calendar sync is handled externally — typically by **DAVx5** for CalDAV accounts.

**Internal package**: `com.kompakt.calendar` (legacy from the KompaktCalendar upstream, deliberately left unrenamed — the applicationId is `com.erdman.erdcal`)

### Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Mudita MMD 1.0.0
- **Architecture**: MVVM — `CalendarViewModel` + `CalendarRepository`
- **Calendar data**: Android `CalendarContract` ContentProvider (no Room, no local DB)
- **Preferences**: Jetpack DataStore (`UserPreferencesRepository`)
- **Notifications**: `AlarmManager` + `BroadcastReceiver` (own stack, not CalendarContract alerts)
- **Min SDK**: 28, Target SDK: 35
- **Build**: Gradle, via the wrapper (`./gradlew`)

---

## Build Commands

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew clean
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Navigation Routes

All routes are registered in `MainActivity.kt` using `NavHost`:

| Route | Screen |
|---|---|
| `calendar` | Month grid view (start destination) |
| `day_view` | Hourly day view with 3 time-page sections |
| `agenda` | Upcoming events list (next 3 months) |
| `add_event?fromCalendar={bool}` | Create or edit event |
| `event_detail/{eventId}` | Read-only event detail |
| `event_search` | Full-text event search |
| `settings` | Preferences screen |
| `notes` | Event description editor (navigated from AddEventScreen) |

---

## Key Source Files

```
app/src/main/java/com/example/helloworld/
├── MainActivity.kt               NavHost, splash screen
├── CalendarViewModel.kt          Central MVVM state — selected date, draft event, permissions
├── CalendarScreen.kt             Month grid view
├── DayViewScreen.kt              Hourly view, time indicator, event overlap layout
├── AgendaScreen.kt               Upcoming events list with e-ink jump-scroll
├── AddEventScreen.kt             Event create/edit with date/time picker wheels
├── EventDetailScreen.kt          Read-only event detail
├── EventSearchScreen.kt          Full-text search
├── SettingsScreen.kt             Calendar visibility, defaults, display options
├── NotesScreen.kt                Event description editor
├── CalendarPermissionGate.kt     Permission request/gate composable
├── MyApplication.kt              App init — notification channel, ContentObserver
├── NotificationScheduler.kt      Schedules AlarmManager exact alarms per event
├── NotificationReceiver.kt       BroadcastReceiver — fires notification when alarm triggers
├── BootReceiver.kt               BroadcastReceiver — reschedules alarms after reboot
├── calendar/
│   ├── CalendarRepository.kt     All CalendarContract queries and writes
│   └── CalendarModels.kt         CalendarEvent, CalendarAccount, EventDraft data classes
└── data/
    └── UserPreferencesRepository.kt  DataStore: week start, week numbers, default calendar/reminder
```

---

## Architecture Details

### CalendarViewModel

The single ViewModel shared across all screens. Key state:

- `selectedDate: StateFlow<LocalDate>` — the currently viewed day
- `currentMonth: StateFlow<YearMonth>` — the displayed month
- `agendaPage: StateFlow<Int>` — DayView time page (0=midnight–noon, 1=8am–8pm, 2=noon–midnight)
- `dayEvents / monthEvents / upcomingEvents` — auto-refresh via `combine` + `flatMapLatest` on `CalendarRepository.changes`
- Draft state (`draftTitle`, `draftStartDate`, etc.) — persists across navigation to Notes and back
- `editingEventId: StateFlow<Long?>` — null for new events, set for edits

`beginNewEvent()` initialises draft state from the currently selected date (not necessarily today).

`saveEvent()` calls `NotificationScheduler` after inserting/updating. `deleteCurrentEvent()` / `deleteEventById()` cancel notifications before deleting.

### CalendarRepository

Wraps `ContentResolver` queries against `CalendarContract.Instances` (expands recurring events), `CalendarContract.Events`, and `CalendarContract.Reminders`.

- All-day events: stored in UTC at midnight, displayed with date-only
- Timed events: stored in system timezone
- `_changes: MutableSharedFlow<Unit>` — emitted on every write and by `MyApplication`'s ContentObserver; drives ViewModel re-fetch

### Notification Stack

`NotificationScheduler` schedules two `AlarmManager.setExactAndAllowWhileIdle` alarms per event:
1. At `startTime - reminderMinutes` — reminder notification
2. At `startTime` — event-start notification

`BootReceiver` queries upcoming events from `CalendarContract.Instances` and reschedules all alarms after device reboot.

Notification channel: `"calendar_events"` (created in `MyApplication.onCreate`).

---

## UI Patterns

### E-ink scroll (no animations)
All scrolling uses jump mechanics — no smooth animations to avoid ghosting:
- **Vertical time pages** in DayView: drag detected with `detectVerticalDragGestures`, jumps between 3 predefined hour ranges
- **Horizontal event columns** in DayView: `detectHorizontalDragGestures`, jumps 1 column (= half event area width) at a time when >2 events overlap
- **AgendaScreen**: custom `LazyColumn` + `canScrollForward`/`canScrollBackward` with jump-step of 4 items; `LazyColumnMMD` was not used because its `isScrollable` heuristic breaks for heterogeneous item heights
- **Settings/date pickers**: swipe gesture on `PickerColumn` with `detectVerticalDragGestures`

### DayView time indicator
`LocalTime.now()` is stored in `mutableStateOf` and updated every 30 seconds via `LaunchedEffect` loop — triggers recomposition to move the indicator line.

### Event overlap layout
Events on the same day are grouped into overlap groups. Groups with ≤ 2 events divide the width equally. Groups with >2 events show 2 columns at a time; `eventColumnOffset` state tracks the horizontal position and resets when the date changes. A row of small indicator bars at the bottom of the event area shows the current horizontal position.

---

## Permissions

| Permission | Purpose |
|---|---|
| `READ_CALENDAR` | Query events and calendars |
| `WRITE_CALENDAR` | Insert, update, delete events and reminders |
| `GET_ACCOUNTS` | List calendar accounts |
| `POST_NOTIFICATIONS` | Show event notifications |
| `USE_EXACT_ALARM` | Exact alarms without user grant (API 33+, calendar app) |
| `SCHEDULE_EXACT_ALARM` | Exact alarms with user grant (API 31–32) |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |

`CalendarPermissionGate` wraps screen content and shows a permission request UI until `READ_CALENDAR` + `WRITE_CALENDAR` are granted.
