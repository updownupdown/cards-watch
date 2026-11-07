# Solitarius for Wear OS

**Bite-sized card and puzzle games designed from the ground up for a round watch face.**

This is the wrist-sized sister of [Solitarius](https://github.com/updownupdown/cards) ([solitarius.app](https://solitarius.app)). It isn't a shrunken phone app: around two dozen games (Aces Up, Clock, Beehive, Memory, Lights Out, Tower of Hanoi and a handful of originals) were chosen and re-laid out so they're comfortable to play with a fingertip and the rotating crown. It runs standalone, with no phone required, and comes with a tile and a watch-face complication for jumping straight back into a game. The full list is in [docs/GAMES.md](docs/GAMES.md).

## Built with

Kotlin and Compose for Wear OS, with baseline profiles for fast startup, unit-tested game rules, and Roborazzi screenshot tests that render every screen.

## Install it on your watch (sideload)

You'll need a watch running Wear OS 3 or later, and `adb` on your computer (it comes with [Android's platform tools](https://developer.android.com/tools/releases/platform-tools)).

1. **Download** the latest `solitarius-watch-….apk` from [Releases](https://github.com/updownupdown/cards-watch/releases/latest).
2. **Turn on developer mode on the watch:** Settings → System → About → Versions, then tap **Build number** seven times.
3. **Turn on debugging:** Settings → Developer options → enable **ADB debugging** and **Wireless debugging**. Keep the watch and your computer on the same Wi-Fi.
4. **Connect:** in Wireless debugging, tap **Pair new device**, then run these with the IP, ports and code the watch shows:
   ```sh
   adb pair <ip>:<pairing-port>
   adb connect <ip>:<port>
   ```
5. **Install:**
   ```sh
   adb install solitarius-watch-<version>.apk
   ```

The app appears in the watch's app list. To update, install a newer APK the same way; your progress is kept.

Tips: if the watch drops off Wi-Fi, put it on its charger while installing. After a restart, run `adb connect` again (the port changes). You can turn Wireless debugging off again afterwards.

Or build it yourself: clone the repo and run `./gradlew :app:installFast` (an optimised build signed with your own debug key), or open the folder in Android Studio and press Run.

## Development

```sh
./gradlew testDebugUnitTest      # unit tests
./gradlew recordRoborazziDebug   # render screens to app/build/outputs/roborazzi
```

Use the `fast` build variant to judge performance; Compose debug builds are many times slower. To publish a sideload APK, bump `versionName` and run **Actions → Release APK → Run workflow**. Play Store release and signing steps are in [docs/RELEASING.md](docs/RELEASING.md).
