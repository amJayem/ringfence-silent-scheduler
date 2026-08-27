# Build Prompt for Claude Code — "Ringfence: Silent Scheduler" Android App

Copy-paste this whole brief into Claude Code as your starting instruction. Attach your
Claude Design prototype (screenshots/exported specs) in the same message if possible.

---

## 1. Project Overview

Build a **native Android app** called **Ringfence: Silent Scheduler**. It automatically switches
the phone to Silent/DND mode during user-defined time windows, and automatically
reverts to Normal/Sound mode afterward. Core use case: people forget to un-mute their
phone after meetings, classes, prayer times, or sleep — this app automates it.

**Phase 1 scope: fully offline.** No backend, no login, no cloud sync. All data
(schedules, settings) stored locally on-device. Design the code so a sync layer
*could* be added later without a rewrite (see Section 7), but do not build any
networking code now.

**Platform:** Android only (iOS has no public API for this — decided already).

**Reference:** Follow the attached Claude Design prototype for screens, flows, and
visual style. Ask me if any screen or interaction is ambiguous rather than guessing.

---

## 2. Core Features to Implement

1. **Time Range Schedule** — user picks start time + end time; phone auto-silences at
   start, auto-reverts at end. Must correctly handle overnight ranges (e.g., 11 PM–7 AM).
2. **Quick Duration Mode** — one-tap "Silent Now" with duration chips (15m/30m/1h/2h/custom).
3. **Multiple recurring daily schedules**, each with day-of-week repeat options.
4. **Home dashboard** — list of schedules with on/off toggles, live countdown, current status.
5. **Settings** — default duration, vibrate-vs-silent choice, notification style, and a
   **Light / Dark / System Default theme toggle** (default = follow system setting).
6. **Do Not Disturb permission flow** — clear onboarding screen explaining *why* the
   app needs "Do Not Disturb access" before requesting it (Android requires this permission
   to be requested via `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`, not a runtime dialog).

---

## 3. Tech Stack (required)

- **Language:** Kotlin (100%, no Java)
- **UI:** Jetpack Compose (not XML layouts)
- **Architecture:** MVVM + a simple domain layer (Clean Architecture-lite — see Section 6)
- **Async:** Kotlin Coroutines + Flow
- **Local storage:** Jetpack DataStore only (Proto DataStore recommended for structured
  schedule data, Preferences DataStore for simple settings) — do NOT use Room, SQLite,
  or any database library; keep storage lightweight and dependency-free
- **Scheduling:** `AlarmManager` with `setExactAndAllowWhileIdle` + `BroadcastReceiver`,
  or `WorkManager` for the reversion job — pick whichever is more reliable for exact-time
  triggers and explain the trade-off in code comments
- **DI:** Hilt
- **Min SDK:** 26 (Android 8.0) — covers ~99% of active devices while allowing modern APIs
- **Target/Compile SDK:** latest stable (required by Play Store policy — verify current
  required target API level before submission, Google updates this yearly)

---

## 4. Play Store Approval — Follow These Rules Closely

Google is strict about apps that touch DND/ringer settings. Build these in from day one,
not as an afterthought:

- **Sensitive permission justification:** `ACCESS_NOTIFICATION_POLICY` (DND access) is a
  sensitive permission. Play Console will ask for a **Permissions Declaration Form** and a
  clear in-app explanation screen shown *before* the system permission prompt. Build this
  explanation screen first.
- **Privacy Policy required:** even for a fully offline app that stores nothing remotely,
  Play Console requires a published privacy policy URL if you request sensitive permissions.
  Draft a simple one stating no data leaves the device — flag this to me so I can host it
  (e.g., a GitHub Pages page or a simple static page).
- **Data Safety form:** fill out Play Console's Data Safety section accurately — since
  Phase 1 is offline, you should be able to declare "no data collected."
- **Background execution limits:** Android 12+ restricts exact alarms. Declare
  `SCHEDULE_EXACT_ALARM` (Android 12+) only if truly needed, and handle the case where
  the user must grant it manually in system settings (Android 13+ requires this as a
  special access, not a runtime prompt). Never request more permissions than needed —
  each unused permission increases review risk and rejection likelihood.
- **Foreground service (if used):** must have a visible, accurate notification explaining
  why it's running ("Ringfence is monitoring your schedule") — Google rejects apps
  with foreground services that don't clearly justify themselves.
- **No deceptive behavior:** don't silence the phone without a clearly visible always-available
  way to cancel/undo — Google review checks for user control over disruptive settings changes.
- **Target API level:** use the current minimum required target SDK for new app submissions
  (verify this at submission time; Google enforces a rolling deadline each year).
- **App icon, screenshots, feature graphic, short/long description:** prepare Play Store
  listing assets as part of the release checklist, not at the last minute.
- **Internal testing track first:** always release to Internal Testing → Closed Testing
  (with the required minimum tester count and 14-day testing period Google now enforces
  for new developer accounts) → Production. Don't attempt to jump straight to production.

---

## 5. Code Quality & Documentation Standards

So any future developer (or you, in six months) can understand the codebase quickly:

- **README.md** at the project root covering: app purpose, architecture diagram/description,
  how to build, how to run, how to test, folder structure explanation.
- **Package-by-feature**, not package-by-layer, e.g.:
  ```
  com.yourapp.silentscheduler/
    core/           (shared utilities, theme, DI modules)
    schedule/       (schedule feature: data, domain, ui)
    quicksilence/   (quick duration feature)
    settings/       (settings feature)
  ```
- **KDoc comments** on all public classes and non-trivial functions — explain *why*,
  not just *what*.
- **No magic numbers/strings** — use named constants or enums (e.g., duration chips,
  DND modes).
- **Consistent naming:** `ViewModel` suffix for ViewModels, `Repository` for data layer,
  `UseCase` for domain logic if you introduce that layer.
- **Unit tests** for ViewModels and domain logic (especially overnight-range time math —
  this is the most bug-prone part of the app). Use JUnit + Turbine for Flow testing.
- **UI tests** for at least the schedule-creation and quick-silence flows, using Compose
  testing APIs.
- **Git commit hygiene:** small, atomic commits with clear messages; no direct commits to
  `main` — use feature branches even if you're the only developer.
- **Lint/formatting:** set up `ktlint` or Android Studio's built-in formatter with a
  checked-in style config so formatting is consistent automatically.

---

## 6. Suggested Architecture (Clean-lite)

```
UI (Compose Screens) 
   → ViewModel (state holder, exposes StateFlow) 
      → UseCase (optional, business logic) 
         → Repository interface (domain layer)
            → Repository implementation (data layer: DataStore only)
```

Keep the **Repository interfaces** in a `domain` layer separate from their
**implementations** in a `data` layer. This is the seam where, if you add sync later,
you swap/extend the repository implementation without touching UI or ViewModels.

---

## 7. Do's and Don'ts

**Do:**
- Do keep the offline data layer isolated behind repository interfaces (for future sync).
- Do write the DND-permission explanation screen before writing the permission-request code.
- Do handle time-zone/overnight edge cases explicitly, with tests.
- Do use Compose previews for every screen so you can iterate visually without running the app.
- Do define your DataStore schema (Proto/data classes) carefully from the start —
  changing the shape of stored data later means writing a manual migration, so keep
  the schedule/settings data models stable and versioned even at v1.
- Do keep the app fully functional with zero network permissions in the manifest for Phase 1.

**Don't:**
- Don't add any analytics/crash-reporting SDK yet without telling me — some (e.g., Firebase)
  require you to update the Data Safety form and privacy policy.
- Don't request `SYSTEM_ALERT_WINDOW`, contacts, location, or any permission not directly
  needed for silencing/scheduling — each one raises review scrutiny.
- Don't hardcode strings in Composables — use `strings.xml` for all user-facing text
  (required for future localization and good practice regardless).
- Don't use deprecated `Ringtone`/`AudioManager` APIs where a modern equivalent exists —
  check current Android documentation for the right approach given the target SDK.
- Don't silently fail if DND permission is revoked later (e.g., user disables it in system
  settings after granting) — detect this and show a clear in-app banner asking to re-grant.
- Don't submit to Production track before testing on at least 2 physical devices with
  different Android versions.

---

## 8. Testing the App on Your Phone While Claude Code Builds

**Option A — USB cable (most reliable):**
1. On your phone: Settings → About phone → tap "Build number" 7 times to enable Developer
   Options.
2. Settings → Developer Options → enable **USB Debugging**.
3. Connect phone to your computer via USB cable.
4. A prompt appears on your phone asking to trust the computer — tap **Allow**.
5. Claude Code (or you, via Android Studio) can now run `adb devices` to confirm the
   phone is detected, then build and install directly with `./gradlew installDebug`
   or by hitting Run in Android Studio.

**Option B — Wireless (no cable needed, same Wi-Fi network):**
1. Enable Developer Options as above.
2. Developer Options → enable **Wireless debugging**.
3. Tap "Wireless debugging" → **Pair device with pairing code**.
4. On your computer terminal: `adb pair <ip>:<port>` (shown on phone), enter the 6-digit
   code when prompted.
5. Then: `adb connect <ip>:<port>` (the main connection port, also shown on the Wireless
   debugging screen).
6. Confirm with `adb devices` — phone should show as connected.
7. Build/install as normal; every rebuild will push straight to your phone over Wi-Fi.

Both your phone and computer must be on the **same Wi-Fi network** for wireless debugging.
USB is more stable for long sessions; wireless is more convenient once paired (it persists
across reboots on the same network in most cases).

---

## 9. Build Screen-by-Screen — Do Not Build Everything at Once

**Important instruction to Claude Code:** build and verify ONE screen/feature at a time,
in the order below. After each one, stop, show me the code, and let me run it on my
phone before moving to the next. Do not scaffold all screens up front and do not move
to the next item until the current one is confirmed working.

1. Scaffold the project only (Gradle, Hilt, Compose, package structure above — no
   screens yet).
2. Build the DND-permission explainer screen + permission request flow. **Stop and
   verify** the permission is actually granted and detected correctly on-device.
3. Build the Quick Silence flow end-to-end (simplest feature, no DataStore needed yet
   beyond a single value). **Stop and verify** the phone actually silences and
   un-silences reliably after the chosen duration — this is the riskiest technical
   piece, confirm it works before building anything else on top of it.
4. Build the Schedule data model + DataStore-based CRUD (add/edit/delete a schedule).
   **Stop and verify** data persists correctly after force-closing the app.
5. Build the Add/Edit Schedule screen (start/end time picker, day-of-week repeat).
   **Stop and verify** against the Claude Design screenshot for that screen.
6. Build the recurring-schedule trigger logic (AlarmManager/WorkManager), including
   overnight range handling. **Stop and verify** with at least one real overnight
   test case.
7. Build the Home Dashboard (schedule list, toggles, live countdown). **Stop and
   verify** against its screenshot.
8. Build the Settings screen (default duration, vibrate option, notification style,
   theme toggle). **Stop and verify** against its screenshot.
9. Only after all screens are individually verified: do a full end-to-end pass —
   multiple overlapping schedules, permission-revoked edge case, dark/light/system
   theme switching.

