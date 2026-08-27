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
- Settings — dark only (default duration, silence style, notifications, theme override)

Not yet seen: **Add/Edit Schedule screen** (needed for step 5), light-mode Settings.
Ask for these before building step 5 / finishing step 8.

## Layout & components (confirmed from source)

- Material 3 throughout.
- Cards: 24dp corner radius.
- Buttons: full pill shape (fully rounded), filled with the accent color for primary
  actions ("Silent now" / "End silence now", "Allow Do Not Disturb access").
- Switches: outlined-track style, filled thumb+track in accent color when on.
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

**Settings (dark)**: back arrow + "Settings" title. "DEFAULT SILENT DURATION" segmented
control with exactly **15m / 30m / 1h / 2h** (confirmed — not seconds), caption "Used
when you tap Silent now without picking a chip." "SILENCE STYLE" two-option toggle
(Full silent / Vibrate only). "NOTIFICATION STYLE" radio list: Banner ("Notify when
silence starts and ends"), Silent log ("No alert, visible in history only"), None
("Never notify"). "APPEARANCE" 3-way Light/Dark/System swatch picker with caption
("System default detected: Dark. Override any time."). Bottom: a "Do Not Disturb
access" status row with a granted/required indicator.
