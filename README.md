# Solitarius for Wear OS

**Bite-sized card and puzzle games designed from the ground up for a round watch face.**

This is the wrist-sized sister of [Solitarius](https://github.com/updownupdown/cards) ([solitarius.app](https://solitarius.app)). It isn't a shrunken phone app: around two dozen games (Aces Up, Clock, Beehive, Memory, Lights Out, Tower of Hanoi and a handful of originals) were chosen and re-laid out so they're comfortable to play with a fingertip and the rotating crown. It runs standalone, with no phone required, and comes with a tile and a watch-face complication for jumping straight back into a game. The full list is in [docs/GAMES.md](docs/GAMES.md).

## Built with

Kotlin and Compose for Wear OS, with baseline profiles for fast startup, unit-tested game rules, and Roborazzi screenshot tests that render every screen.

## Install it on your watch (sideload)

You build it from source and push it to the watch over Wi-Fi. You'll need [Android Studio](https://developer.android.com/studio) (which brings the Android SDK, `adb` and a JDK) and a watch running Wear OS 3 or later.

1. **Turn on developer mode on the watch:** Settings → System → About → Versions, then tap **Build number** seven times.
2. **Turn on debugging:** Settings → Developer options → enable **ADB debugging** and **Wireless debugging**. Keep the watch and your computer on the same Wi-Fi.
3. **Connect:** in Wireless debugging, tap **Pair new device** and run, with the IP, port and code the watch shows:
   ```sh
   adb pair <ip>:<pairing-port>
   adb connect <ip>:<port>
   ```
4. **Build and install** the optimised `fast` build (small and quick, signed with a debug key):
   ```sh
   git clone https://github.com/updownupdown/cards-watch
   cd cards-watch
   ./gradlew :app:installFast
   ```
   Or open the folder in Android Studio, pick the watch as the target and press Run.

Tip: if the watch drops off Wi-Fi, put it on its charger while installing. After a restart, run `adb connect` again (the port changes).

## Development

```sh
./gradlew testDebugUnitTest      # unit tests
./gradlew recordRoborazziDebug   # render screens to app/build/outputs/roborazzi
```

Use the `fast` build variant to judge performance; Compose debug builds are many times slower. Release and signing steps are in [docs/RELEASING.md](docs/RELEASING.md).
