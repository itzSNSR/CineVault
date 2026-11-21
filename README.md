# CineVault

**CineVault by SNSR** — a fast, cinematic Android app for movies, TV shows, live TV and anime. Built with Kotlin and Jetpack Compose.

![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

---

## Features

- **Discover** — trending and popular movies and series in a scrollable, image-rich home feed
- **Search** — instant search with suggestions across the whole catalog
- **Details** — posters, ratings, cast, seasons and episode lists
- **Streaming** — ExoPlayer (Media3) playback with HLS/DASH, subtitle support and quality selection
- **Offline downloads** — save titles to `Movies/CineVault` and play them later in the built-in player
- **Watchlist, favourites and history** — stored locally with Room
- **Library** — Continue Watching, watchlist, history and recent searches in one place
- **Watch Together** — shared rooms for synced group watching
- **Live TV** — IPTV playlists with a dedicated player
- **OTT hub** — quick access to where a title is available across major platforms
- **Anime** — a second content provider for anime series with progress sync
- **Chromecast** — cast playback to a TV with a foreground media service
- **Picture-in-Picture** — keep watching while using other apps
- **In-app updates** — silent update checks against a public `version.json`

Everything ships in one APK, no account required to browse.

---

## Official website

The download landing page source lives in [`docs/`](docs/) and is ready for GitHub Pages
from `main` → `/docs`. It is plain HTML, CSS and JavaScript with no build step; see
[`docs/README.md`](docs/README.md) for local preview, asset and deployment details.
Enable Pages in the repository settings to serve it at
**https://itzsnsr.github.io/CineVault/**; the hosted site is not enabled yet.

---

## What's new in 4.7

- **Midnight Amethyst liquid glass** — native translucent glass surfaces across Home, Search, Details, Library and Live TV
- **CineVault wordmark and liquid navigation** — a stable brand lockup and draggable bottom navigation with spring motion
- **Branded loading and buffering** — the CineVault-to-Play transition and compact buffering morph
- **Player gestures and HUDs** — brightness, media-volume and horizontal seek gestures with glass overlays
- **Seek Lock** — lock accidental seek gestures during online and offline playback
- **Compatibility preserved** — Android 7.0+, existing providers and playback behavior remain unchanged

---

## Tech stack

| Layer | Libraries |
|---|---|
| Language / UI | Kotlin 2.2, Jetpack Compose, Material 3, Navigation Compose |
| Architecture | MVVM, ViewModel + StateFlow, Repository pattern |
| Local data | Room (favourites, history, downloads), SharedPreferences |
| Networking | Retrofit, OkHttp, Moshi, coroutines |
| Media | AndroidX Media3 ExoPlayer (HLS / DASH), MediaRouter, Cast framework |
| Images | Coil with SVG support |

---

## Requirements

| Tool | Version |
|---|---|
| Android | 7.0 (API 24) or newer |
| JDK | 17 |
| Android SDK | Platform 36.1, Build-Tools 36.1.0 |
| Gradle | 9.3.1 (wrapper included) |

---

## Build from source

```bash
git clone https://github.com/itzSNSR/CineVault.git
cd CineVault

# debug build (installable, debug-signed)
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

### Release build

Create a keystore once and keep it safe — every future update must be signed with the same key:

```bash
keytool -genkeypair -v -keystore release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias cinevault -storepass <password> -keypass <password> \
  -dname "CN=CineVault, O=SNSR, C=IN"
```

Then create `key.properties` in the project root:

```properties
storeFile=../release-key.jks
storePassword=<password>
keyAlias=cinevault
keyPassword=<password>
```

```bash
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

`release-key.jks`, `key.properties` and `local.properties` are ignored by git — never commit them.

---

## In-app updates

The app reads a small manifest on startup and when you tap **Settings → Check for Updates**:

```json
{
  "versionName": "4.7",
  "versionCode": 12,
  "forceUpdate": false,
  "changelog": "• What changed in this build",
  "downloadUrl": "https://github.com/itzSNSR/CineVault/releases/download/v4.7/CineVault.apk"
}
```

It is fetched from `version.json` on `main` (jsDelivr CDN, with raw GitHub as fallback). If the remote `versionCode` is higher than the installed one, an update dialog appears — or a blocking screen when `forceUpdate` is `true`.

**Publishing a new version:**

1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`
2. Build with `./gradlew assembleRelease`
3. Create a GitHub Release with a `vX.Y` tag and attach the APK named `CineVault.apk`
4. Update `version.json` to match — new `versionCode`, `versionName`, `changelog` and `downloadUrl`
5. Commit and push

---

## Privacy and third-party services

History, watchlist and profile sync rely on **third-party services**. The app warns about this on first launch and on the sign-in screen:

> CineVault uses third-party services for your history, watchlist and profile sync. For your safety, please use a secondary email address — or use the app without signing in.

Browsing, searching and playback work without signing in.

---

## Disclaimer

CineVault is an independent client and is **not affiliated with, endorsed by, or sponsored by** any streaming service or content provider. The app does not host or upload media; it displays content discovered from third-party APIs. Availability and legality of content vary by region — you are responsible for complying with the laws and terms of service that apply to you. Provided "as is", without warranty of any kind.

---

## Community

- Telegram: [t.me/+q5bGgCcZcFpiNWQ1](https://t.me/+q5bGgCcZcFpiNWQ1)
- Releases: [github.com/itzSNSR/CineVault/releases](https://github.com/itzSNSR/CineVault/releases)

---

## License

MIT © 2026 SNSR — see [LICENSE](LICENSE).
