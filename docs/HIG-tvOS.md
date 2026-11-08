# tvOS 26 design rules used by tvhome

This document is the design contract for `com.hamad.tvhome`. Every value is in
**tvOS points (pt)** and converted at runtime; see [Scaling](#1-canvas-and-scaling).
Sources are the crawled Apple HIG pages in `docs/hig/` (`typography.txt`,
`layout.txt`, `focus-and-selection.txt`, `tab-bars.txt`, `materials.txt`,
`top-shelf.txt`, `motion.txt`). Where the implementation intentionally departs
from the HIG, the deviation is listed in [Deviations](#10-deviations).

## 1. Canvas and scaling

| Property | tvOS reference | tvhome |
| --- | --- | --- |
| Canvas | 1920 x 1080 pt | 1280 x 720 px (target box, mdpi) |
| Conversion | – | `dp = pt * screenHeightDp / 1080` |
| Reference density | 320 dpi, 540 dp tall | 160 dpi, 720 dp tall |
| dp per pt | 0.5 | 0.667 |

- tvOS authors in points on a fixed 1920x1080 canvas. Android TV boxes are
  fragmented, so the app scales the whole ramp from the *actual* screen height:
  `TvMetrics(dpPerPt)`, produced by `LocalTvMetrics` and `TvType`.
- On a 1080p / 320 dpi panel this is exactly the specified `dp = pt / 2`.
- Never hard-code `dp` for design values. Author points with `m.dp(320f)` so the
  layout stays proportional on 720p and 4K boxes.
- Font sizes go through `m.sp(pt)`, and tracking is expressed in points then
  converted with the same factor, which preserves the HIG's 1/1000 em ratios at
  every scale.

## 2. Safe area

From `layout.txt`: *"Inset primary content 60 points from the top and bottom of
the screen, and 80 points from the sides."*

| Edge | Minimum inset |
| --- | --- |
| Top | 60 pt |
| Bottom | 60 pt |
| Left / right | 80 pt |

- The launcher uses 80 pt side padding on every shelf, the app grid, and the
  hero copy. The bottom breathes with an 80 pt trailing spacer (the 60 pt
  minimum plus optical room).
- Top chrome follows the tab-bar rule instead: its top edge is 46 pt from the
  screen top (see below), which already clears the 60 pt safe band because the
  bar itself is 68 pt tall.
- Content that scrolls under the chrome may enter the top inset; focusable
  controls may not.

## 3. Typography

tvOS built-in text styles (`typography.txt`, "tvOS built-in text styles"), with
the matching tracking value at each size from the tvOS tracking table:

| Style | Size | Leading | Weight | Emphasized | Tracking (pt) |
| --- | --- | --- | --- | --- | --- |
| Title 1 | 76 | 96 | Medium (500) | Bold | +0.07 |
| Title 2 | 57 | 66 | Medium | Bold | +0.29 |
| Title 3 | 48 | 56 | Medium | Bold | +0.35 |
| Headline | 38 | 46 | Medium | Bold | +0.37 |
| Subtitle 1 | 38 | 46 | Regular (400) | Medium | +0.37 |
| Callout | 31 | 38 | Medium | Bold | +0.39 |
| Body | 29 | 36 | Medium | Bold | +0.40 |
| Caption 1 | 25 | 32 | Medium | Bold | +0.15 |
| Caption 2 | 23 | 30 | Medium | Bold | −0.10 |

Rules:

- `TvType` is the only source of text styles (`title1 … caption2`). Screens ask
  for a role, never for a raw size.
- Tracking is applied as `trackingPt * dpPerPt` sp. Because the font size is
  scaled by the same factor, the em ratio equals `trackingPt / sizePt`, i.e. the
  1/1000 em values in the HIG table.
- Emphasized styles are derived with `style.copy(fontWeight = FontWeight.Bold)`;
  do not invent intermediate sizes.
- SF Pro is not redistributable. Latin text uses **Inter** (variable, weights
  300–700), the closest open metric match. Arabic text uses **IBM Plex Sans
  Arabic** (400/500/600), which has proper joins and diacritics.
- Arabic must not inherit Latin tracking: `TvType.arabic(role)` sets the Arabic
  family and zero letter spacing. The Arabic surfaces (prayer screensaver,
  Arabic settings labels) also set `LayoutDirection.Rtl` locally.
- All text is rendered with foundation `BasicText` via `TvText`; Material is not
  on the classpath.

## 4. Color and materials

- Base canvas is pure black (`#000000`) so posters and Aerial footage carry the
  color. Elevated surfaces use `#0C0C0E`.
- Label hierarchy: 100 % / 78 % / 58 % / 40 % white. Nothing dimmer than 40 %
  for focusable text.
- System accent palette follows the iOS/tvOS dark palette (`#0A84FF` blue,
  `#30D158` green, `#FF453A` red, …) in `TvColor`.
- Focused platters are **opaque white with black text**, unfocused labels are
  white at 58–78 %. This is the tvOS "highlight" appearance and keeps contrast
  independent of the artwork behind it.
- Materials: the tab bar is a regular Liquid Glass surface — blurred, luminosity
  adjusted, translucent, with a 1 px inner highlight and a soft outer shadow.
  Never stack two glass layers; use the `elevated` variant for the floating bar
  only.
- If a background is busy (hero artwork), keep a dimming scrim under text
  (bottom-up black gradient) instead of increasing glass opacity.

## 5. Layout grid

Five-column grid from `layout.txt`:

| Attribute | Value |
| --- | --- |
| Unfocused content width | 320 pt |
| Horizontal spacing | 40 pt |
| Minimum vertical spacing | 100 pt |

Application:

- **App grid**: 5 columns, 320 x 192 pt tiles (5:3, the Apple TV icon ratio),
  40 pt horizontal gaps, 60 pt row gaps. On the 720p box this fills the width
  exactly (5 x 213 px + 4 x 27 px + 2 x 53 px = 1280 px), so five is also the
  maximum that fits without shrinking the HIG card size.
- **Media shelves**: 320 pt wide cards, 40 pt gaps, 80 pt side insets, shelf
  title in Headline, 46 pt above the row.
- **Hero**: 16:9 artwork, copy constrained to 820 pt so lines stay readable;
  the pager snaps one item at a time and keeps the tab bar clear.
- Focus growth is included in the spacing budget: a focused tile scales 1.07,
  so adjacent gaps must remain > 0 at maximum scale.

## 6. Tab bar

From `tab-bars.txt`: height **68 pt**, top edge **46 pt** from the top of the
screen, translucent by default, only the selected tab opaque, and the selected
tab gains a drop shadow while the bar is focused.

Implemented in `TabBar`:

- Total height 68 pt, radius 34 pt (fully rounded), centered horizontally.
- Items, in order: Search (icon), Home, Movies, TV Shows, Apps, Settings (icon).
- A 13 % white pill marks the selected tab at rest; a white platter with an
  18 dp shadow follows focus. Both are springs, so the pill slides rather than
  fades.
- The clock sits top-right at the same 46 pt optical line, in Body at 82 %
  white, and updates every 5 s.
- The bar auto-hides (alpha 0, −40 pt shift) while focus is inside a shelf or a
  grid row below the first, and returns on Menu/Up. This mirrors the tvOS
  scroll-away behavior without stealing vertical space.

## 7. Focus and selection

- Focus is a **pure state machine** (`LauncherState` + `LauncherReducer`), not a
  set of `focusRequester`s. Directions are resolved from the state, which makes
  every transition unit-testable and deterministic.
- Only one item is focused at a time. Focus is never moved without a directional
  input, except when the focused item disappears — then it falls back to the
  nearest valid item (HIG: avoid unsolicited focus changes).
- Visual focus = scale + platter + shadow + label inversion. No idle animation:
  nothing pulses or drifts when the user is still.
- Long press (650 ms on DPAD_CENTER) opens the action sheet; a shorter press
  selects. The key-up after a long press is swallowed.
- Menu returns focus to the tab bar first, then exits to the system launcher.
- Every focusable node exposes semantics (`contentDescription`, `selected`,
  `role = Button`) so TalkBack and `uiautomator` can name it.

## 8. Motion

- Motion communicates hierarchy only: focus platter, tab indicator, sheet, and
  hero cross-fade. No ambient or looping animation outside jiggle mode.
- Defaults: `TvSpring` (medium-low stiffness, no bounce) for platters and
  scales; `TvDpSpring` for positions/sizes; 250 ms tween for chrome show/hide;
  8 s auto-advance for the hero while focus is on the tab bar or hero.
- Jiggle mode is the sole exception to "no idle animation": it runs only while
  the app grid is in edit mode, at ±1.6° / 140 ms, and stops on exit.
- Respect the system "remove animations" setting by snapping springs to their
  target when `Settings.Global.ANIMATOR_DURATION_SCALE == 0`.

## 9. Surface-specific rules

These are the specifications for the remaining surfaces; they are implemented
progressively, and anything without real data hides itself rather than showing
placeholder art.

### Top Shelf
- Apple TV's system Top Shelf has no Android TV counterpart, so tvhome renders
  its own: the hero pager (TMDB trending) followed by the first content row.
  When TMDB returns nothing, the hero is omitted and the first shelf moves up
  into the space instead of showing skeleton cards.

### Search
- Full-screen, black, 80 pt side insets. The keyboard is a custom 6-column grid
  (foundation only), focusable with the D-pad.
- Query debounce 300 ms; results appear in the five-column poster grid.
- Selecting a result opens the detail page using `tmdb_id` + `tmdb_type` so the
  page is deep-linkable from the Top Shelf and from search history.

### Settings
- Split layout: 320 pt sidebar list, content pane to the right, tab bar pinned
  at the top. Rows are 68 pt tall with 16 pt radius; the focused row inverts to
  a white platter.
- Real values only: version from `BuildConfig`, IP from `LinkProperties`, storage
  from `StatFs`, TMDB status from the repository. No fake toggles.

### Control Center / App Switcher
- A right-side control panel over a dimmed launcher scrim; a full-width recents
  row sits below it.
- Wi-Fi and Bluetooth tiles show the live system state (transport, adapter) and
  opening them goes to the matching system settings; recents come from real
  usage stats and launch the real app.
- Focus is trapped inside the overlay; Menu or Back closes it, long-press Home
  toggles it.
- Implemented through an Accessibility Service because a normal app cannot draw
  over the system launcher; the service is opt-in and can be revoked in system
  settings. See `docs/SETUP.md`.
- The scrim is dimmed rather than blurred: an overlay cannot capture another
  app's surface on API 29, and a synthetic blurred screenshot would be fake.

### Screen saver (one daydream, two styles)
- A single `DreamService` (`dream/TvHomeDreamService.kt`) is the system screen
  saver and shows whichever style is selected in Settings, so the picker has a
  real effect without a second system entry.
- Aerial: video-only, no UI. Plays a real 1080p Aerial clip from the Apple CDN,
  cross-fades between clips, shows a small location caption in Caption 1 for the
  first 8 s, then hides it.
- Prayer: RTL, Arabic family. Shows the next prayer, city, Gregorian and Hijri
  dates, and a countdown. Calculation method and coordinates come from settings;
  without a location the screen shows an explanation, not fake times.
- Times are computed locally (no network dependency) and recomputed at midnight
  and on timezone changes.
- Both pause/release on dream exit and never keep the network awake when idle.

## 10. Deviations

| HIG | tvhome | Why |
| --- | --- | --- |
| System focus effects / parallax layers | Programmatic scale + platter + shadow | Compose foundation has no tvOS focus engine; parallax needs layered art the TMDB API does not provide |
| 100 pt minimum vertical grid spacing | 60 pt between 192 pt app rows | Five rows of 5:3 icons already exceed the HIG's optical gap; 100 pt would push the second row off a 720p screen |
| SF Pro | Inter (Latin), IBM Plex Sans Arabic | Licensing; metrics are close and tracking is matched to the HIG table |
| tvOS system tab bar | Custom canvas bar with the same 68 pt height and 46 pt top offset | Foundation-only, no tv-material |
| System screensaver picker | App-provided DreamServices | Android TV has no Apple Aerial service |

## 11. Implementation map

| Concern | File |
| --- | --- |
| Point-to-dp metrics, type ramp, tracking, palettes | `ui/TvTheme.kt` |
| Text rendering | `ui/TvText.kt` |
| Icons (Canvas, no vector assets) | `ui/TvIcons.kt` |
| Glass, springs, focus platter mods | `ui/Glass.kt` |
| Focus reducer / navigation | `launcher/LauncherState.kt` |
| Loading, prefetch, key routing, actions | `launcher/LauncherViewModel.kt` |
| Chrome, hero, tab bar | `launcher/LauncherScreen.kt` |
| Shelves, app grid, action sheet | `launcher/LauncherParts.kt` |
| TMDB data | `data/TmdbClient.kt`, `data/TmdbRepository.kt` |
| Installed apps and banners | `data/AppRepository.kt` |
| Preferences | `data/TvPrefs.kt` |
| Control Center overlay | `overlay/ControlCenterView.kt`, `overlay/TvAccessibilityService.kt` |
| Control Center live data | `overlay/ControlCenterData.kt` |
| Control Center focus rules | `overlay/ControlCenterNavigation.kt` |
| Screen saver (dispatcher) | `dream/TvHomeDreamService.kt` |
| Device provisioning | `docs/SETUP.md` |

## 12. Verification

- `uiautomator dump` proves text, bounds and focus order.
- Pixel statistics (non-black ratio, per-region luminance) prove that posters,
  scrims and platters actually rendered; screenshots are captured with
  `adb shell screencap` + `adb pull`.
- `dumpsys gfxinfo com.hamad.tvhome framestats` measures jank in the launcher;
  the budget is 60 fps with no frame over 32 ms while idle.
- Unit tests cover the reducer (tab wrap, grid edges, row memory), long-press
  timing, Aerial manifest parsing, and prayer-time math.
