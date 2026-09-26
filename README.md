# mdwriter

A private, offline Markdown editor for Android 16/17 in the spirit of iA Writer. No accounts, no
network permission — your notes never leave the phone. This is a personal, sideloaded app: you build
and install it yourself with `make install`.

> **Back up your signing key.** The first `make install` creates `~/.config/mdwriter/release.jks` and
> `keystore.properties`. Every future update must be signed with it; without it the only way to update is
> uninstalling, which deletes the notes stored inside the app. `make keystore-info` shows where they are.

## Install on your phone

mdwriter runs on **Android 16 or 17**. You install it from this Mac with one command; no Play Store,
no account. Your notes are stored inside the app on the phone.

### 1. One-time setup on the Mac
- Install **Android Studio** (it brings the Android SDK, `adb` and a Java runtime). Nothing else is
  needed; you do not have to put `adb` on your PATH.
- Check everything: `make doctor`

### 2. One-time setup on the phone
1. **Enable Developer options:** Settings > About phone > tap **Build number** 7 times, enter your
   PIN ("You are now a developer!").
2. Open **Settings > System > Developer options**.

Then pick **one** way to connect:

**USB cable (simplest)**
1. In Developer options, turn on **USB debugging**.
2. Plug the phone into the Mac. On the phone, accept **Allow USB debugging?**; tick
   **Always allow from this computer**.

**Wi-Fi (no cable; phone and Mac on the same Wi-Fi network)**
1. In Developer options, tap **Wireless debugging**, turn it on, and allow it on this network.
2. Tap **Pair device with pairing code**. The phone shows a 6-digit code and an
   *IP address & port*.
3. On the Mac: `make pair HOST=192.168.1.23:37123 CODE=123456` (use the values from the phone).
4. The phone normally connects by itself a few seconds later (`make devices` shows it). If it
   does not: `make connect HOST=<IP address & port shown on the main Wireless debugging screen>`
   (this port is different from the pairing port).
   Pairing is remembered; next time just turn Wireless debugging on.

### 3. Install / update
```sh
make install        # builds, installs (or updates) and opens mdwriter; your notes are kept
```
Run the same command after every `git pull`: each build gets a higher version number, so it is
always an in-place update. Several phones connected? `make install DEVICE=<serial>` (see `make devices`).
You can turn Developer options off again afterwards; the app keeps working.

### 4. Your signing key: back it up now
The first `make install` creates `~/.config/mdwriter/release.jks` and
`~/.config/mdwriter/keystore.properties`. **Every future update must be signed with this key.**
Copy both files to your password manager or an encrypted backup (`make keystore-info` shows where
they are and their fingerprint). On a new Mac, put them back in `~/.config/mdwriter/` *before*
running `make install`.

### Troubleshooting
| Message | Meaning / fix |
|---|---|
| `no Android device connected` | USB: cable/port, USB debugging on, accept the prompt. Wi-Fi: Wireless debugging on, same network, `make pair` again. |
| device `unauthorized` | Unlock the phone and accept **Allow USB debugging?**. No prompt? Developer options > **Revoke USB debugging authorizations**, unplug, replug. |
| `2 devices connected - choose one` | `make install DEVICE=<serial>` (serials from `make devices`). |
| `needs Android 16 (API 36) or newer` / `INSTALL_FAILED_OLDER_SDK` | The phone runs Android 15 or older; mdwriter supports Android 16 and 17 only. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (signed with a DIFFERENT key) | The app on the phone was signed with another key (new Mac, lost `~/.config/mdwriter`). **Nothing was changed on the phone.** Restore your backed-up key files and run `make install` again. Only if the key is truly lost: export your notes first, then `make uninstall CONFIRM=yes && make install` (uninstalling deletes the notes inside the app). |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | The phone has a newer build (Mac clock wrong?). `make install VERSION_CODE=<bigger number>`. |
| `INSTALL_FAILED_USER_RESTRICTED` | Accept the prompt on the phone; on Xiaomi/Oppo/Vivo also enable Developer options > **Install via USB**. |
| Samsung: USB commands ignored | Settings > Security and privacy > **Auto Blocker** blocks USB commands; turn it off while installing. |
| `offline` device | Unplug/replug, or restart adb: `~/Library/Android/sdk/platform-tools/adb kill-server`. |
| `no JDK 17-26 found` | Install Android Studio, or `brew install openjdk@21`. Gradle cannot run on Java 27. |

## Development

| Command | What it does |
|---|---|
| `make help` | List all targets. |
| `make doctor` | Check JDK, SDK, signing key and connected devices. |
| `make emulator` | Boot the default AVD (`Pixel_10_Pro_XL`) and wait until ready. |
| `make devices` | List connected devices with their Android version. |
| `make install-debug DEVICE=<serial>` | Build + install + launch the debug app (separate `.debug` app, its own notes). |
| `make run-debug` | Launch the installed debug app (no build). |
| `make install` | Build the release app, install/upgrade it (keeps notes), then launch. |
| `make run` | Launch the installed release app (no build). |
| `make test` | Run all JVM unit tests (every module). |
| `make test-device DEVICE=<serial>` | Run instrumented tests on the connected device/emulator. |
| `make lint` | Run Android Lint. |
| `make format` | Auto-format Kotlin + Gradle files (Spotless + ktlint). |
| `make check` | What CI runs: format check, lint, unit tests, release build (R8). |
| `make clean` | Delete build outputs (never touches the signing key or the phone). |
| `make logcat` | Stream the app's logs (release + debug); survives app restarts. |
| `make keystore-info` | Show where the release signing key is and its fingerprint. |
| `make backup-notes` | Copy the debug app's notes to `./notes-backup-<time>/`. |
| `make uninstall CONFIRM=yes` | Uninstall the release app. **Deletes every note stored inside it.** |

Requirements: Android Studio (brings the SDK + a JDK). Nothing needs to be on `PATH`. For raw Gradle
calls: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` (Gradle 9 cannot run on Homebrew's JDK 27).

## Project layout

- `app/` — the Android app: Compose UI, the View-based editor engine, storage, settings.
- `core/markdown/` — a pure Kotlin/JVM Markdown engine (highlighter, SmartEdit, stats, HTML export).
- `plans/` — the implementation plan this app is built from.

### Fonts

iA Writer Duo, Quattro, Mono by Information Architects Inc., SIL OFL 1.1 — see `app/src/main/assets/licenses/`.
