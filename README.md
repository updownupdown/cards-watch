# Solitarius for Wear OS

A small collection of playing-card games for round Wear OS watches, sister to
the Solitarius phone app. Kotlin + Compose for Wear OS.

## Setup (Windows)

1. Clone into a Windows folder such as `C:\dev\cards-watch` (not inside WSL:
   Android Studio, adb and the emulator all run natively on Windows, and
   building across the WSL file system is slow).
2. Open the folder in Android Studio. It installs the SDK it needs
   (compileSdk 37) and syncs Gradle.

## Running on the watch (wireless debugging)

1. Watch: **Settings → System → About → Versions**, tap **Build number**
   7 times.
2. **Settings → Developer options**: turn on **ADB debugging** and
   **Wireless debugging**. Watch and PC on the same Wi-Fi.
3. Android Studio: **Device Manager → Pair devices using Wi-Fi**, or
   `adb pair <ip>:<port>` with the watch's code, then `adb connect <ip>:<port>`.
4. Pick the watch as the run target and press Run.

If the watch drops off Wi-Fi, put it on its charger while testing. After a
restart, `adb connect` again (the port changes).

## Speed: use the `fast` build

Debug builds of Compose run many times slower than release builds (no code
shrinking or optimisation, debuggable runtime), and the debug APK is ~38 MB to
push over Wi-Fi. To judge speed, switch **Build Variants** (View → Tool Windows
→ Build Variants) to **fast**: optimised like a release, signed with the debug
key, ~2 MB. Use **debug** only when you need the debugger.

## Emulator

Device Manager → Create device → Wear OS → Small Round and Large Round. Good for
layout checks; tap feel, the crown and haptics need the real watch.

## Checks

```
./gradlew testDebugUnitTest         # unit tests
./gradlew recordRoborazziDebug      # renders screens to app/build/outputs/roborazzi
```
