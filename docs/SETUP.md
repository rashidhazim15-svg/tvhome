# Device setup

Everything below is optional and reversible. Replace `192.168.1.104:5555` with
the box's address if it changed. The app id is `com.hamad.tvhome`.

## 1. Install and make tvhome the Home app

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell cmd package set-home-activity com.hamad.tvhome/.launcher.MainActivity
```

Revert to the stock launcher with `adb shell cmd package set-home-activity
com.bluetv.launcher/<activity>` (or clear the default under **Settings → Apps →
Default apps → Home**).

The stock launcher does not need to be uninstalled or disabled; choosing tvhome
as the Home activity is enough and is a single-setting revert.

## 2. Aerial / Prayer screen saver (daydream)

```powershell
adb shell settings put secure screensaver_components com.hamad.tvhome/com.hamad.tvhome.dream.TvHomeDreamService
adb shell settings put secure screensaver_enabled 1
adb shell settings put secure screensaver_activate_on_sleep 1
adb shell settings put system screen_off_timeout 120000
```

Switch between Aerial and Prayer in **tvhome → Settings → Screen Saver →
Screen saver**. The single system daydream (`TvHomeDreamService`) reads that
choice, so the selected saver is what the TV plays. **Preview** shows it
instantly over Settings; the system entry is **Settings → Screen Saver →
tvhome Screen Saver**.

Revert:

```powershell
adb shell settings put secure screensaver_components com.android.dreams.basic/com.android.dreams.basic.Colors
```

The launcher, Search, Detail and Settings deliberately do not hold
`FLAG_KEEP_SCREEN_ON`, so any of them can idle into the daydream. Only the
screen-saver previews keep the display awake. Do not add the flag back.

## 3. Control Center (accessibility overlay)

tvOS opens Control Center on a long Home press. Android does not let a launcher
draw over other apps, so tvhome uses an accessibility service that the user
enables explicitly:

1. **tvhome → Settings → General → Control Center** and select it, or
   `adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS`.
2. Enable **tvhome Control Center** under *Services*.
3. Long-press Home (0.5 s) anywhere: the overlay shows real Wi-Fi/Bluetooth
   state plus recent apps. D-pad moves, Enter opens the focused item, Menu or
   Back closes. **Preview Control Center** in the same section shows it over Settings.

Revert by disabling the service in the same screen, or:

```powershell
adb shell settings put secure enabled_accessibility_services ""
adb shell settings put secure accessibility_enabled 0
```

> Clearing those settings turns off *all* accessibility services; disable only
> tvhome if other services are in use.

## 4. Recent apps data

The App Switcher reads real usage stats. Grant access once:

```powershell
adb shell appops set com.hamad.tvhome GET_USAGE_STATS allow
```

Or **tvhome → Settings → General → Recent apps access**. Without the grant the
overlay simply says recent apps are unavailable — it never invents entries.

Revert: `adb shell appops set com.hamad.tvhome GET_USAGE_STATS default`.

## 5. Build

```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
.\gradlew.bat :app:assembleRelease --console=plain
```

Release builds are minified and signed with the debug key. `TMDB_READ_TOKEN`
lives only in the git-excluded `local.properties`; without it the launcher
hides media rows and Search reports that TMDB is not configured.

## 6. Verify

```powershell
adb shell screencap -p /sdcard/shot.png; adb pull /sdcard/shot.png
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml
adb shell dumpsys gfxinfo com.hamad.tvhome framestats
.\gradlew.bat :app:testReleaseUnitTest
```
