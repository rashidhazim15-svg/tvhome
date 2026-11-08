# tv home

A personal, non-commercial Android TV launcher that brings an Apple TV (tvOS 26)
style experience to a real Android TV box.

## What it does

- **Home / Movies / TV Shows / Apps** tabs with a focus-driven tab bar, a
  full-width hero (Top Shelf) and media shelves using real artwork.
- **Your Apps**: real installed apps with banners/icons, focus platters, a
  jiggle mode for moving icons and an action sheet (open, move, hide).
- **Search**: an on-screen D-pad keyboard and a 4-column results grid backed by
  the real TMDB search API.
- **Details**: backdrop, logo, year, genres, rating and overview for a movie or
  show, opened from the launcher or `tvhome://detail` deep links.
- **Settings**: region, clock, screen saver, prayer calculation, network and
  about, with real device/network values.
- **Screen saver**: a single Android daydream that plays either Apple Aerial
  clips (streamed from Apple's CDN) or an Arabic prayer-times screen. The
  selected style is read from Settings, and times are computed locally.
- **Control Center**: an opt-in accessibility-service overlay (long-press Home)
  with real Wi-Fi/Bluetooth state and recent apps.

Everything shown is real device or network data. When a source is unavailable
(for example no TMDB token), the UI hides that content instead of showing
placeholder data.

## Tech

- Kotlin 2.2, Jetpack Compose **foundation only** (no Material / TV Material),
  custom Canvas-drawn chrome, icons and glass surfaces.
- Media3 for Aerial video, Coil for artwork, OkHttp for HTTP.
- minSdk 26, targetSdk 34, compileSdk 36. Release builds are minified and
  signed; no debug features are compiled in.
- Pure state-machine navigation with JVM unit tests for the launcher, search,
  Control Center focus, prayer math and the Aerial catalogue parser.

## Build

Requirements: JDK 17 and the Android SDK.

```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
.\gradlew.bat :app:assembleRelease
```

Local configuration goes in `local.properties` (git-ignored, never committed):

```properties
# TMDB v4 read access token
TMDB_READ_TOKEN=...
```

Without it the launcher hides media rows and Search reports that TMDB is not
configured. Device provisioning (default Home, screen saver, accessibility,
usage access) is documented in `docs/SETUP.md`; the design contract lives in
`docs/HIG-tvOS.md`.

## Attribution

This product uses the TMDB API but is not endorsed or certified by TMDB.

Aerial clips are streamed from Apple's public catalogue; this project is not
affiliated with or endorsed by Apple. Android TV is a trademark of Google LLC.
