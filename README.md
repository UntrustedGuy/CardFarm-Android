# CardFarm — a native Android Steam card farmer & game idler

CardFarm is a **native, open-source Android app** (Kotlin + Jetpack Compose) that
logs into your Steam account and **farms trading-card drops** or **idles a chosen
set of games** — the same core things [ArchiSteamFarm](https://github.com/JustArchiNET/ArchiSteamFarm)
does, but as a real Android app. No Termux, no .NET runtime, no PC required.

> This is an independent project and is **not** affiliated with or endorsed by
> Valve or the ArchiSteamFarm project.

## How it works

Rather than porting ASF's C#/.NET codebase, CardFarm talks to the Steam network
directly using **[JavaSteam](https://github.com/Longi94/JavaSteam)** — a mature,
open-source Java/Kotlin port of Valve's SteamKit2 (the very library ASF itself is
built on). That gives us, natively on Android:

- Real Steam login over the Steam network protocol (not web scraping for auth)
- Steam Guard support (Mobile Authenticator **and** email codes)
- Password-less reconnects using an encrypted, on-device refresh token
- Sending the `ClientGamesPlayed` message to idle games (what makes cards drop)

The **card-farming policy** is implemented ASF-style on top: it scrapes your
`steamcommunity.com` badge pages to find games with remaining card drops, idles
them one at a time, and re-checks periodically until everything is farmed.

## Features

- 🔐 **Secure login** — password is sent only to Steam and never stored. Only an
  encrypted Steam refresh token is kept on-device (`EncryptedSharedPreferences`).
- 🛡️ **Steam Guard** — in-app dialog for both authenticator and email codes.
- 🃏 **One-game card farming** — finds games with drops and farms them one at a time.
- 🎮 **Custom game idling** — enter one App ID to idle (great for playtime).
- 👁️ **Steam visibility** — appear Online or Offline to friends while CardFarm runs.
- 📊 **Live dashboard** — remaining drops, games to farm, and per-game status.
- 🔄 **Background service** — a foreground service keeps farming while the app is
  minimized, with an ongoing notification and a one-tap sign-out.
- ♻️ **Auto-resume** — reconnects and resumes farming after network drops or a
  cold start, using the saved session. Removing CardFarm from Recents stops its
  Steam connection and notification.

## Project layout

```
app/src/main/java/io/github/untrustedguy/cardfarm/
├─ CardFarmApp.kt           # Application; wires JavaSteam logging
├─ MainActivity.kt          # Compose entry point, login/dashboard switch
├─ data/
│  └─ SessionStore.kt       # Encrypted refresh-token / session storage
├─ steam/
│  ├─ Models.kt             # State enums, commands, BadgeGame, GuardRequest
│  ├─ FarmRepository.kt     # UI <-> service state bridge (flows + channel)
│  ├─ SteamController.kt    # JavaSteam client, connect/auth/logon, playGames
│  ├─ FarmingEngine.kt      # ASF-style farming policy + web token minting
│  ├─ BadgeScraper.kt       # steamcommunity badge-page parser (jsoup)
│  ├─ UiAuthenticator.kt    # Bridges Steam Guard prompts to the UI
│  └─ SteamFarmService.kt   # Foreground service + notification
└─ ui/
   ├─ FarmViewModel.kt      # Exposes flows, forwards user actions
   ├─ LoginScreen.kt        # Login UI
   ├─ DashboardScreen.kt    # Farming dashboard + badge list
   ├─ CustomIdleDialog.kt   # "Idle specific App IDs" dialog
   ├─ Components.kt         # Logo, Steam Guard dialog, small widgets
   └─ theme/Theme.kt        # Steam-inspired Material 3 dark theme
```

## Building

### In a Codespace / any machine with the Android SDK

```bash
# Debug APK
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Release APK (unsigned)
./gradlew assembleRelease
```

Requirements: **JDK 17** and the Android SDK (`compileSdk 35`, `minSdk 26`).
If Gradle can't find the SDK, create a `local.properties` file:

```
sdk.dir=/path/to/Android/Sdk
```

### Via GitHub Actions

Every push to `main` runs `.github/workflows/build.yml`, which builds the debug
APK and uploads it as a downloadable artifact (**Actions → latest run →
`cardfarm-debug-apk`**). You can also trigger it manually with **Run workflow**.

## Tech stack

- Kotlin 2.0, Coroutines
- Jetpack Compose + Material 3
- JavaSteam 1.8.0 (Steam network client)
- OkHttp + jsoup (badge scraping)
- Coil (game capsule images)
- AndroidX Security Crypto (encrypted session)

## Notes & limitations

- Steam only drops cards for a handful of games at a time and on a delay; farming
  is intentionally paced (a badge re-scan every ~15 minutes) to match this.
- Cards only drop for games where you have drops remaining.
- Keep the app allowed to run in the background (disable battery optimization for
  it) for uninterrupted farming.
- Use responsibly and in line with the Steam Subscriber Agreement. This tool is
  provided for educational purposes.

## License

See [LICENSE](LICENSE).
