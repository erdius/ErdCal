# ErdCal — Project Facts

Personal fork of KompaktCalendar (itself derived from CalmDirectory), an
Android calendar app built with Jetpack Compose, optimized for the Mudita
Kompakt's E-ink display via the Mudita Mindful Design (MMD) library. Runs
on any Android 9+ device.

## Stack

- Kotlin, Jetpack Compose, Mudita MMD 1.0.0
- MVVM: `CalendarViewModel` + `CalendarRepository`
- No local database — all event data lives in the Android Calendar
  Provider (`CalendarContract`: Events, Instances, Reminders), synced
  externally (typically by DAVx5 for CalDAV accounts)
- Preferences: Jetpack DataStore (`UserPreferencesRepository`)
- Notifications: own `AlarmManager` + `BroadcastReceiver` stack (not
  CalendarContract's own alert mechanism) — reminder notification(s)
  before an event, a notification at event start, snooze support, a
  `BootReceiver` to reschedule alarms after reboot, and a dedicated
  full-screen alert Activity at event start that wakes the screen and
  bypasses the lock screen
- Min SDK 28, target SDK 35

## Naming

Internal Kotlin package is `com.kompakt.calendar` (legacy from the
KompaktCalendar upstream, deliberately left unrenamed) but the
`applicationId` is `com.erdman.erdcal`. Don't be confused by the mismatch.

## Release/signing

`keystore.properties` (gitignored) configures real release signing.
Release APK output is named `ErdCal-<versionName>.apk` (see
`applicationVariants.all` in `app/build.gradle`). Current at time of
writing: versionCode 7 / versionName 1.3.3.

## Remotes

`origin` = `erdius/ErdCal` (the fork, push here). `upstream` =
`codeberg.org/davidanderlohr/KompaktCalendar` (the original — never push
here, fetch-only for reference).

## Documentation trust

`README.md` and `WARP.md` at the repo root are both accurate and current
for this specific fork (verified this session) — unlike some sibling
projects in this workspace, nothing here is a stale pre-fork leftover.
Safe to use for orientation, but always verify specifics against the
actual source before relying on them for a bug-hunt contract.
