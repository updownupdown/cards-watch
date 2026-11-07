# Releasing

## Sideload APK (GitHub Releases)

`.github/workflows/release.yml` builds the release variant signed with a separate
sideload key (repository secrets `SIDELOAD_KEYSTORE_BASE64` and
`SIDELOAD_KEYSTORE_PASSWORD`, alias `sideload`) and attaches
`solitarius-watch-v<version>.apk` to a GitHub Release. Bump `versionName`, then
run Actions → Release APK → Run workflow (or push a `v*` tag). Keep using the
same sideload key so installs can update. A sideloaded build and a Play build are
signed differently, so one has to be uninstalled before the other goes on.

## Play Store

The watch is uploaded to the phone app's Play listing, on its Wear OS track,
signed with the phone's upload key.

1. In the phone app, `eas credentials` → Android → production → download the
   keystore. Put it beside this folder (not in it), e.g. `../solitarius-upload.jks`.
2. Create `keystore.properties` here (ignored by git) with the values EAS shows:
   ```
   storeFile=../solitarius-upload.jks
   storePassword=…
   keyAlias=…
   keyPassword=…
   ```
3. Bump `versionName` in `app/build.gradle.kts` (the version code follows it).
4. `./gradlew :app:bundleRelease`, then upload
   `app/build/outputs/bundle/release/app-release.aab` to the Wear OS track.

## Speed: use the `fast` build

Debug builds of Compose run many times slower than release builds (no code
shrinking or optimisation, debuggable runtime), and the debug APK is ~38 MB to
push over Wi-Fi. To judge speed, switch **Build Variants** (View → Tool Windows
→ Build Variants) to **fast**: optimised like a release, signed with the debug
key, ~2 MB. Use **debug** only when you need the debugger.

