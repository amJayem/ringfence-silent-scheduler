# Design Notes

Living record of visual decisions, kept in sync as screens are built. Source: the
Claude Design canvas ("Silent Scheduler Android.dc.html"). The share link itself
403'd and the raw HTML source was never available, so this is built from two
raster exports: a print-to-PDF (`Silent Scheduler app design.pdf`) and a clearer
PNG (`Silent Scheduler Android-selection.png`). **Colors below are pixel-sampled
from the PNG with a color picker (Python/Pillow), not eyeballed — high confidence.**
Layout/copy details are read directly off the images.

Note: the screens still say "SILENT SCHEDULER" — that's the app's pre-rename name.
The app itself is **Ringfence: Silent Scheduler**; treat "Silent Scheduler" in the
source images as a stand-in for wherever the app's name/wordmark appears, not
literal copy to ship.

## Screens covered by the source images

- Dashboard — light and dark
- Onboarding — Do Not Disturb access request
- Settings — light and dark (default duration, silence style, notifications, theme override)
- Add/Edit Schedule ("Edit window") — light
- "Silent now" — a bottom sheet (not a full screen), light and dark

All screens the build order calls for have now been seen at least once.

## Layout & components (confirmed from source)

- Material 3 throughout.
- Cards: 24dp corner radius.
- Buttons: full pill shape (fully rounded), filled with the accent color for primary
  actions ("Silent now" / "End silence now", "Allow Do Not Disturb access").
- Switches: flat filled-thumb toggle, no Material default checkmark icon — outlined
  track when off, filled accent track + white thumb when on. The stock M3 `Switch`
  didn't match this (checkmark-in-thumb, different outline treatment, especially
  noticeable in light mode), so it's a custom composable: `core/ui/PillSwitch.kt`.
- FAB: 60dp, circular, accent-colored, bottom-right, overlapping the schedule list,
  for adding a new schedule window.
- Bottom nav: 3 items (Schedules, Silent now, Settings); the active item sits inside an
  "accent-soft" pill background.
- Countdown / time numerals use a monospace font (IBM Plex Mono in the source).
- Motion: 220ms standard transition duration for state changes (toggle, theme switch).
- Light and dark share identical layout; only color tokens swap.
- Theme follows OS by default; Settings has a manual Light/Dark/System override
  (segmented 3-way control with a preview swatch per option).

## Color tokens (pixel-sampled from the PNG — high confidence)

| Token | Light | Dark |
|---|---|---|
| Accent (primary) | `#7C86C6` | `#8D96D4` |
| Accent soft (active nav pill, badges) | `#ECEDF5` | `#292A38` |
| Background | `#F4F3F1` | `#101013` |
| Surface (cards) | `#FFFFFF` | `#1A1A1F` |
| On-surface (primary text) | `#17171A` | `#FCFCFC` |
| On-surface variant (secondary text) | `#8B8B8B` | `#8B8B8B` |
| Outline / dividers | `#E7E7E8` | `#48494B` |

Defined in `app/src/main/kotlin/.../core/theme/Color.kt` and wired into
`MaterialTheme` in `Theme.kt`.

## Typography

- Numerals (countdown, schedule times): monospace. Using `FontFamily.Monospace` as a
  stand-in until an actual IBM Plex Mono `.ttf` is added under `res/font/`.
- Everything else: Material 3 default type scale (not yet customized — no non-numeral
  type specifics were visible in the source images so far).

## Screen-specific notes

**Dashboard**: small-caps label ("SILENT SCHEDULER") + day greeting ("Thursday")
header, two icon buttons top-right ("+" add schedule, a sliders/filter icon). Big
card: circular countdown ring (accent arc over a faint track), a small "⊘ SILENT"
state badge, large monospace time remaining ("7h 24m"), caption naming the active
schedule and end time ("Sleep · until 7:00 AM"), then a full-width pill button whose
label depends on state — **"End silence now"** while a window is active (caption
below: "Sound returns automatically. No action needed."), or **"Silent now"** when
idle. Below that, a "RECURRING SCHEDULES" section header with an "N of M on" counter,
then cards per schedule (label, time range, recurrence + silence style e.g. "Weekdays
· vibrate", "Next in Xh Ym" or "NOW" badge + "Silent until …" when currently active,
on/off switch). FAB overlaps the list bottom-right.

**Onboarding (DND access)**: centered icon in a soft accent-tinted circle, headline
"Your phone remembers to un-mute.", body copy, a 3-item bullet list (Do Not Disturb
access / runs on a schedule not in the background / always reverts), primary pill CTA
"Allow Do Not Disturb access", secondary text-only "Not now" below it. Matches
CLAUDE.md's requirement to explain before requesting the permission.

**Settings (light + dark)**: back arrow + "Settings" title. "DEFAULT SILENT DURATION"
segmented control with exactly **15m / 30m / 1h / 2h** (confirmed — not seconds),
caption "Used when you tap Silent now without picking a chip." "SILENCE STYLE"
two-option toggle (Full silent / Vibrate only) — this is the *default* used for new
schedules and Quick Silence; each schedule can override it individually (see Add/Edit
Schedule below). "NOTIFICATION STYLE" radio list: Banner ("Notify when silence starts
and ends"), Silent log ("No alert, visible in history only"), None ("Never notify").
"APPEARANCE" 3-way Light/Dark/System swatch picker with caption ("System default
detected: Dark. Override any time."). Bottom: a "Do Not Disturb access" status row
with a granted/required indicator. Layout is identical across themes; only color
tokens swap.

**Add/Edit Schedule ("Edit window")**: back chevron + title ("Add window" / "Edit
window") — no Save in the header; Save lives in a fixed bottom action bar instead.
"LABEL" section: a single bordered text field. "WINDOW" section: Start/End side-by-
side bordered boxes (mono time), tapping one makes it the *active target* — a 1.5dp
accent border that persists on whichever card was tapped most recently, independent
of whether its time dialog is currently open — plus a duration caption below ("20m of
silence") normally, or an inline error card when start = end ("Start and end are the
same time" / "A window needs at least 5 minutes. For a full 24 hours, use two
schedules instead.").

**Deliberate deviation from INTERACTION-SPEC.md's E-06/E-07**: the source design
specifies a scrolling list of every 15-minute slot across 24 hours as the Start/End
picker. Per explicit direction, the build instead keeps its existing numeric time
entry (hour/minute typed digits + AM/PM, with a dial-view toggle) rather than building
that scrolling list — everything else in E-03 through E-11 is implemented as designed.

Under the Start/End cards, a 24-hour bar (E-08/E-09/E-10) draws the window on a
midnight-to-midnight Canvas track with ticks and mono time labels at 12 AM / 6 AM /
12 PM / 6 PM / 12 AM: one accent segment for a same-day window, or two segments
(start-to-right-edge, left-edge-to-end) for an overnight window like 9:30 AM→2:30 AM,
shown together with a "Crosses midnight" pill tag next to the duration caption. When
start = end, no segment is drawn — the track stays empty and the tag doesn't appear.
Verified on-device for both the same-day and overnight cases; the invalid (start=end)
case shares the same code gate as the overnight case and wasn't separately re-verified
by tapping through the UI, but is correct by inspection.

"REPEAT" section: a Sunday-first row of 7 circular day chips (S M T W T F S — accent-
filled when selected) plus "Every day"/"Weekdays"/"Weekends" quick-select pill presets
below. "SILENCE STYLE": the same segmented control as Settings, but scoped to this one
schedule (defaults to the Settings-wide value for a brand-new schedule). No "Enabled"
toggle here — that's the Dashboard row's switch, not part of this screen. "Delete
schedule": a full-width outlined pill button with red/error-colored text, shown only
when editing an existing schedule.

**"Silent now" (bottom sheet, not a screen)**: reachable from the Dashboard's idle
status card *and* the bottom-nav "Silent now" item — both open the same sheet rather
than two different UIs. Title "Silent now", body copy ("Sound comes back
automatically. This does not touch your recurring schedules."), a row of 4 duration
chips (15m/30m/1h/2h) each showing a "til H:MM AM/PM" preview of when that duration
would end, a "Custom" row (label + "Silent until H:MM" caption) with a −/value/+
stepper, and a single "Start custom silence" pill CTA that starts silence for whatever
duration is currently selected (a chip tap sets the custom value to that chip's
minutes; the stepper then fine-tunes from there in 5-minute steps).
