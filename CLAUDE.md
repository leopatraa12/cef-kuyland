# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Note: repo docs and code comments are in Portuguese; this file is in English but references those files.

## Project overview

Android APK source for "News RP", a SA-MP (San Andreas Multiplayer) mobile client: a Java launcher (server list, downloader, editor, host tools), an in-game HUD with WebView overlays (HTML/CSS/JS), and a native C/C++ layer (SA-MP client + GTA:SA hooks) built via the Android NDK. Gradle root project name is `Medusa`; Android application ID is `com.xyron.game`.

## Build commands

Requires Android Studio, Android SDK Platform 33, and NDK 27/26/25/21 (searched in that order). If Gradle can't find Java on the CLI, set `JAVA_HOME` or `org.gradle.java.home` in `gradle.properties` to Android Studio's bundled JBR.

Build debug APK (PowerShell):
```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug --no-daemon
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

Install to a connected device:
```powershell
adb devices
adb install -r -d -g app/build/outputs/apk/debug/app-debug.apk
```

There are no unit/instrumentation test suites wired into this workflow beyond the default Gradle-generated `androidTest`/`test` stubs in `prdownloader`; there is no custom lint/test command beyond the standard `gradlew test` / `gradlew connectedAndroidTest`.

### Native library (libSAMP.so)

The native SA-MP client source lives in `jni/jni` and is **not** wired into the Gradle build (the `externalNativeBuild` block in `app/build.gradle` is commented out) — it must be built separately with `ndk-build` and the resulting `.so` copied manually into `jniLibs`:
```powershell
cd jni
.\compile.cmd          # locates ndk-build.cmd and runs it
.\mover.cmd             # copies libs\armeabi-v7a\libSAMP.so into ..\app\src\main\jniLibs\armeabi-v7a\
```
Only `armeabi-v7a` is supported (`abiFilters 'armeabi-v7a'` in `app/build.gradle`); arm64 is not ported.

Never guess a `libGTASA.so` memory offset. Any change that depends on a GTA:SA internal address must be backed by a logcat/tombstone dump or a signature scanner — see `jni/jni/vendor` and existing hook code for patterns.

### Debugging native crashes
```powershell
adb logcat -c
adb logcat -v time | Select-String "FATAL EXCEPTION|Fatal signal|SIGSEGV|SIGABRT|libGTASA|libSAMP|libsamp"
```
Look for `Build fingerprint`, `pid`/`tid`, `signal`, `fault addr`, `backtrace`, and the offending library+offset (e.g. `libGTASA.so pc 005a576a`). A crash in `libGTASA.so CCustomRoadsignMgr::Initialise` at boot usually means the Data Lite payload is incomplete (missing `texdb/txd`, `texdb/samp`, etc. — see Data Lite section below).

## Architecture

### Two-stage app: launcher → game activity

- `app/src/main/java/com/xyron/game/launcher/` — pre-game app: `EntryActivity` (LAUNCHER entry point) → `MainActivity` (server list/home/editor/host fragments, under `launcher/fragments`), `SplashActivity`, `UpdateActivity`, and `UpdateService` (background downloader for game data, uses the local `prdownloader` module). `launcher/util/GameDataVerifier.java` validates that required Data Lite/Full files exist before letting the game launch. `launcher/data` and `launcher/adapters` back the server-list UI.
- `app/src/main/java/com/xyron/game/main/` — the actual game activity: `GTASA.java` (extends the closed-source `WarMedia` base from the bundled `sampmobilecef-*.aar`, handles OBB/expansion-file setup and lifecycle logging, loads `ImmEmulatorJ`/`SCAnd`/`GTASA` native libs) → `SAMP.java` (~2900 lines; the real activity — HUD click handling, WebView overlay lifecycle, JNI bridge to native code, `RuntimeOverlayBridge` for JS↔Java calls). `main/ui` and `main/ui/dialog` hold in-game Android dialogs.
- `com.raiferoleplay.game.*` (`game/SAMP.java`, `cef/CefBrowser.java`) is a **compatibility shim**: some recovered/prebuilt `libSAMP.so` binaries still resolve JNI symbols under the original `com.raiferoleplay` package names via `JNI_OnLoad`. `raiferoleplay.game.game.SAMP` extends `com.xyron.game.main.GTASA` and `raiferoleplay.game.cef.CefBrowser` forwards to `com.xyron.game.main.SAMP`. Don't remove these unless you know the native lib you're linking against no longer needs them.

### WebView overlays (in-game UI)

In-game UI (inventory, phone, map, weapon wheel) is rendered as offline WebViews loaded from `app/src/main/assets/interfaces/<name>/index.html`, hosted inside `FrameLayout`s in `app/src/main/res/layout/hud.xml`, and driven from `SAMP.java`:
- Overlay show/hide methods (`showInventoryOverlay`, `showWeaponWheelOverlay`, `showPhoneOverlay`, …) and URL constants live in `SAMP.java`.
- `initializeRuntimeOverlays` wires up `findViewById` for overlay views; `ensureRuntimeOverlayConfigured` has a per-overlay case/switch.
- JS calls into Java via `window.Android.*`, implemented as `@JavascriptInterface` methods on `RuntimeOverlayBridge` in `SAMP.java`.
- Java pushes data into JS via `evaluateJavascript`, e.g. weapon wheel state is synced through `UpdateWeaponWheel` / `syncWeaponWheelPayload` as JSON (`[{ "id": 0, "ammo": 0, "current": true }]`).
- Full walkthrough (including how to add a brand-new overlay) is in `docs/INTERFACE_EDITING.md` — read it before touching HUD/WebView code.

### What requires a native rebuild vs. just Gradle

Editing `hud.xml`, `res/drawable`, any file under `assets/interfaces`, launcher layouts/fragments, or `SAMP.java`'s Java-side logic only needs a Gradle rebuild. Reading new player data not yet exposed to Java, adding a new `native` method, touching hooks/RakNet/pools/textdraws/radar/native chat/GTA memory, or adding a new JNI bridge requires editing `jni/jni` and rebuilding `libSAMP.so` (see Native library section above). `docs/INTERFACE_EDITING.md` has the full breakdown.

### Native layer (`jni/jni`)

C/C++ SA-MP client source built with `ndk-build` (`Android.mk` / `Application.mk`). Key subdirs: `game` (GTA:SA hooks, RenderWare under `game/RW`), `net` (RakNet networking), `client` (`client/cpp`), `gui` (`samp_widgets`, `widgets`), `voice_new` (voice chat), `java` (JNI glue back to the Java side), `vendor` (third-party: RakNet, BASS audio, CEF, curl, imgui, opus, str_obfuscator, armhook, etc.). Entry points are in `main.cpp`/`main.h`.

### Data Lite / Data Full downloads

Download sources are declared in `app/src/main/assets/update_sources.json` and fetched by `UpdateService.java` into `/sdcard/Android/data/com.xyron.game/files`. Data Lite must contain at minimum `texdb/txd/txd.*`, `texdb/samp/samp.*`, `texdb/samp.img`, `texdb/gta3.img`, `texdb/gta_int.img`, and `SAMP/main.scm` or the game crashes natively on boot; `GameDataVerifier.java` enforces this before launch.

### Gradle modules

- `:app` — the APK itself (see above).
- `:prdownloader` — local fork/module of the PRDownloader library, used by `UpdateService` for game-data downloads.

Both are declared in `settings.gradle` (root project name `Medusa`).

## Repository conventions / constraints

- This is a **not a git repository** in the current checkout (no `.git` present) — do not assume `git` history/blame is available.
- The public repo intentionally excludes prebuilt binaries and proprietary assets: `.so`/`.a`/`.aar`/`.jar` libs, APKs, Data Lite/Full payloads. `app/src/main/jniLibs` and `app/libs` (e.g. `sampmobilecef-1.0.0-release.aar`) may be present locally as prebuilt deps but are not a substitute for the `jni/jni` source.
- `app/google-services.json` in this checkout has placeholder Firebase data — anyone shipping a real build needs to swap in their own.
- Never invent/hardcode a `libGTASA.so` offset (see Debugging section).
