# CLAUDE.md

This file is automatically read by Claude Code at the start of every session in this
project folder. Keep it accurate and up to date — it's the single source of truth for
how this codebase should be built and maintained.

## Project

**Ringfence: Silent Scheduler** — a native Android app that automatically switches the phone to
Silent/DND mode during user-defined time windows and auto-reverts to Sound mode
afterward, so users don't forget to un-mute after meetings, classes, prayer, or sleep.

## Phase & Scope

- **Current phase: Phase 1 — fully offline.** No backend, no login, no cloud sync.
- Android only. No iOS work in this repo (iOS has no public API for programmatic DND
  control and would need an entirely different design — not in scope here).
- Design so a future sync layer *could* be added without a rewrite (keep repository
  interfaces separate from implementations — see Architecture below), but do not write
  any networking code now.

## Locked-In Tech Stack — Do Not Change Without Explicit Approval

- Kotlin (100%, no Java)
- Jetpack Compose (no XML layouts)
- MVVM + light domain layer (Clean Architecture-lite)
- Kotlin Coroutines + Flow
- **Storage: Jetpack DataStore only.** No Room, no SQLite, no database library of any
  kind. Proto DataStore for structured schedule data, Preferences DataStore for simple
  settings.
- AlarmManager / WorkManager for silence/un-silence scheduling triggers
- Hilt for dependency injection
- Min SDK 26; target/compile SDK = latest stable required by Play Store at submission
  time (check current requirement before release, Google updates this yearly)

If you (Claude Code) think a different tool would work better for something, say so
and explain why — but don't switch silently.

## Architecture

```
UI (Compose Screens)
   → ViewModel (state holder, exposes StateFlow)
      → UseCase (optional, business logic)
         → Repository interface (domain layer)
            → Repository implementation (data layer: DataStore only)
```

Package-by-feature, not package-by-layer:
```
com.yourapp.silentscheduler/
  core/           (shared utilities, theme, DI modules)
  schedule/       (schedule feature: data, domain, ui)
  quicksilence/   (quick duration feature)
  settings/       (settings feature)
```

## Build Order — Screen by Screen, Not All at Once

Always build and verify one screen/feature at a time. Stop after each one, show the
code, and wait for confirmation it works on a real device before starting the next.
Full order is documented in `silent-scheduler-claude-code-prompt.md` in this repo —
follow it in sequence:
1. Project scaffold only
2. DND permission explainer + request flow
3. Quick Silence flow (simplest feature, proves the silencing mechanism works)
4. Schedule data model + DataStore CRUD
5. Add/Edit Schedule screen
6. Recurring schedule trigger logic (including overnight range handling)
7. Home Dashboard
8. Settings screen (incl. theme toggle)
9. Full end-to-end pass only after all of the above are individually verified

## Design Reference

Follow the Claude Design prototype screenshots for all screens exactly — layout,
spacing, colors, component styles. If a screen or interaction is ambiguous, ask
rather than guessing.

As visual decisions get locked in (colors, spacing units, font choices), record them
in `DESIGN_NOTES.md` in this repo and keep it updated — this keeps every session
visually consistent even across long gaps.

## Do's

- Keep the data layer isolated behind repository interfaces (for a possible future
  sync layer).
- Write the DND-permission explanation screen before writing the permission-request
  code.
- Handle overnight time ranges (e.g., 11 PM–7 AM) explicitly, with tests.
- Use Compose previews for every screen.
- Keep DataStore schemas (Proto/data classes) stable and versioned from v1 — changing
  their shape later requires a manual migration.
- Keep the app fully functional with zero network permissions in the manifest.
- Add KDoc comments on public classes/functions, explaining *why* not just *what*.
- Use named constants/enums — no magic numbers or strings.
- Put all user-facing text in `strings.xml`, never hardcoded in Composables.
- Write unit tests for ViewModels and domain logic, especially overnight-range time
  math. Write UI tests for schedule-creation and quick-silence flows.
- Use small, atomic git commits with clear messages; feature branches, not direct
  commits to main.

## Don'ts

- Don't use Room, SQLite, or any database library — DataStore only.
- Don't add analytics/crash-reporting SDKs without explicit approval first (affects
  Play Store's Data Safety form and privacy policy).
- Don't request any permission beyond what silencing/scheduling strictly needs
  (no `SYSTEM_ALERT_WINDOW`, contacts, location, etc.).
- Don't use deprecated Ringtone/AudioManager APIs where a modern equivalent exists.
- Don't silently fail if DND permission is revoked later — detect it and show an
  in-app banner asking the user to re-grant.
- Don't build multiple screens before the current one is verified working.
- Don't submit to the Play Store Production track before testing on 2+ physical
  devices with different Android versions.

## Play Store Approval Notes

- `ACCESS_NOTIFICATION_POLICY` (DND access) is a sensitive permission — needs a clear
  in-app explanation screen before the system prompt, plus Play Console's Permissions
  Declaration Form.
- A published privacy policy URL is required even for this offline app, because of the
  sensitive permission.
- Fill out the Data Safety form accurately — Phase 1 should qualify as "no data
  collected."
- `SCHEDULE_EXACT_ALARM` (Android 12+) only if truly needed; Android 13+ requires this
  as a special access granted in system settings, not a runtime dialog.
- Any foreground service must show a visible, accurate notification explaining why
  it's running.
- Always give the user a clearly visible way to cancel/undo an active silence —
  Google reviews for user control over disruptive settings changes.
- Release path: Internal Testing → Closed Testing (meet Google's minimum tester count
  and testing period) → Production. Never skip straight to Production.

## Testing on a Physical Device

See `silent-scheduler-claude-code-prompt.md`, Section 8, for full USB and wireless ADB
setup instructions. Quick summary: enable Developer Options + USB Debugging (or
Wireless debugging) on the phone, then `adb devices` to confirm connection, then
`./gradlew installDebug` or run from Android Studio.

## Related Files in This Repo

- `silent-scheduler-claude-code-prompt.md` — full original build brief (detailed
  version of everything summarized above)
- `DESIGN_NOTES.md` — living record of visual decisions (create this once the first
  screen's design choices are locked in)
- `README.md` — should be created early: app purpose, architecture summary, how to
  build/run/test, folder structure
