# Design Notes

Living record of visual decisions, kept in sync as screens are built. Source: the
Claude Design canvas ("Silent Scheduler Android.dc.html"), read from a print-to-PDF
export (`Silent Scheduler app design.pdf`) since the share link itself 403'd and the
raw HTML source was not available. **Colors below are eyeballed from a rasterized
screenshot, not read from source CSS — treat hex values as placeholders and correct
them against the real .dc.html file or a color-picked screenshot as soon as either is
available.**

## Screens covered by the source file

- Dashboard — light and dark
- Onboarding — Do Not Disturb access request
- Settings — dark (default duration, silence style, notifications, theme override)

Not yet seen: Add/Edit Schedule screen, Quick Silence screen, light-mode Settings.
Ask for these (or the full canvas) before building steps 5 and 8.

## Layout & components (confirmed from source)

- Material 3 throughout.
- Cards: 24dp corner radius.
- Buttons: full pill shape (fully rounded), filled with the accent color for primary
  actions ("Silent now", "Allow Do Not Disturb access").
- Switches: outlined-track style, filled thumb+track in accent color when on.
- FAB: 60dp, circular, accent-colored, bottom-right, for adding a new schedule window.
- Bottom nav: 3 items (Schedules, Silent now, Settings); the active item sits inside an
  "accentSoft" pill background.
- Countdown / time numerals use a monospace font (IBM Plex Mono in the source).
- Motion: 220ms standard transition duration for state changes (toggle, theme switch).
- Light and dark share identical layout; only color tokens swap.
- Theme follows OS by default; Settings has a manual Light/Dark/System override
  (segmented 3-way control with a preview swatch per option).

## Color tokens (placeholder — verify against source)

| Token | Light | Dark |
|---|---|---|
| Accent (primary) | `#6C63E8` | `#8B83F5` |
| Accent soft (active nav pill, badges) | `#E7E4FB` | `#2A2740` |
| Background | `#F7F7F9` | `#121218` |
| Surface (cards) | `#FFFFFF` | `#1C1C24` |
| On-surface (primary text) | `#1B1B1F` | `#F2F2F5` |
| On-surface variant (secondary text) | `#6B6B70` | `#A0A0A8` |
| Outline / dividers | `#E1E1E6` | `#2E2E38` |

Defined in `app/src/main/kotlin/.../core/theme/Color.kt` and wired into
`MaterialTheme` in `Theme.kt`.

## Typography

- Numerals (countdown, schedule times): monospace. Using `FontFamily.Monospace` as a
  stand-in until an actual IBM Plex Mono `.ttf` is added under `res/font/`.
- Everything else: Material 3 default type scale (not yet customized — no non-numeral
  type specifics were visible in the source pages read so far).

## Screen-specific notes

**Dashboard**: header greeting ("Thursday"), big countdown ring/card ("2h 55m until
Dhuhr prayer at 1:15 PM"), primary "Silent now" pill button, then a "RECURRING
SCHEDULES" list of cards (label, time range, recurrence, on/off switch), FAB overlaps
the list bottom-right.

**Onboarding (DND access)**: centered icon, headline "Your phone remembers to
un-mute.", body copy, a 3-item bullet list explaining why DND access is needed / that
it's schedule-only (no background location/contacts/network) / that it always reverts,
primary pill CTA "Allow Do Not Disturb access", secondary text-only "Not now" below it.
Matches CLAUDE.md's requirement to explain before requesting the permission.

**Settings (dark)**: back arrow + "Settings" title, "DEFAULT SILENCE DURATION" segmented
control (15s/30s/1h/2h — note: likely "15m/30m" for minutes, confirm exact values),
"SILENCE STYLE" two-option toggle (Full silent / Vibrate only), "NOTIFICATION STYLE"
radio list (Banner / Silent log / None), "APPEARANCE" 3-way Light/Dark/System picker,
and a "Do Not Disturb access" status row at the bottom (Granted/required indicator).
