# mdwriter

A private, offline Markdown editor for Android 16/17 in the spirit of iA Writer. No accounts, no
network permission — your notes never leave the phone. This is a personal, sideloaded app: you build
and install it yourself with `make install`.

> **Back up your signing key.** The first `make install` creates `~/.config/mdwriter/release.jks` and
> `keystore.properties`. Every future update must be signed with it; without it the only way to update is
> uninstalling, which deletes the notes stored inside the app. `make keystore-info` shows where they are.

## Features

- Live-styled Markdown: headings, emphasis, code, quotes, lists, tasks and links are styled as you type; the Markdown
  markers stay visible but quiet.
- Focus mode (dim everything but the current sentence or paragraph), typewriter scrolling, and an optional word count.
- Preview: a clean rendered view of the note, fully offline.
- Find and replace.
- A library of notes in the app, plus linked folders anywhere on the phone (your files stay in your folder).
- Share a note, open `.md`/`.txt` files from other apps, export all notes as a zip.
- Light, dark and pure-black themes; three typefaces (Duo, Quattro, Mono); six text sizes.

## Gestures

- Swipe from the start edge toward the end (left to right in LTR) to open the **library**; swipe the other way to open the
  **preview**. The swipes never fight text selection, cursor drags or vertical scrolling. Turn them off in Settings
  ("Swipe to library & preview").
- Select text to get the formatting pill (bold, italic, link, code, headings, lists, ...).
- Tap a task box (`- [ ]`) to toggle it.
- Tap near the top of the screen to bring the two corner buttons back; they fade out while you type.
- **Back** closes things in this order: keyboard, selection, find bar, library (search, then up a folder, then close),
  preview, then leaves the app.

## Keyboard shortcuts

With a hardware keyboard (press **Meta+/** in the app to see the list).

| Shortcut | Action |
|---|---|
| Ctrl+N | New note |
| Ctrl+L | Toggle the library |
| Ctrl+Z / Ctrl+Shift+Z | Undo / Redo |
| Ctrl+F | Find |
| Ctrl+B / Ctrl+I | Bold / Italic |
| Ctrl+K | Link |
| Ctrl+Shift+C | Code |
| Ctrl+Shift+X | Strikethrough |
| Ctrl+0 ... Ctrl+6 | Body text / Heading 1-6 |
| Ctrl+R | Preview |

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

## Updating

`git pull && make install`. Your notes are kept: each build has a higher version number, so it is an in-place update.
Never uninstall the app to "reinstall" it: that deletes the notes stored inside it.

## Back up your signing key

`make keystore-info` shows where the key is and its fingerprint. Copy the whole `~/.config/mdwriter/` folder to an
encrypted backup. Without that key you cannot update the app without uninstalling it, which deletes the notes stored
inside the app.

## Where your notes live

- **In-app library:** app-private storage, invisible to other apps, removed when the app is uninstalled. Auto Backup is on
  for the notes (`library/`) and settings (`datastore/`): Google backup (only on devices with encrypted backup) and
  device-to-device transfer may copy them. Recovery copies and the trash are never backed up.
- **Linked folders:** the files stay in your own folder; mdwriter only edits the notes you open.
- The debug app `dev.mdwriter.debug` is a separate app with separate notes. `make backup-notes` copies the debug app's
  notes only; the release app's private files cannot be read over adb.

## Export

- **Export all notes:** Settings (or long-press "On this device" in the library) creates a `.zip` through the system file picker.
- **Share** a single note from the overflow menu or the preview.
- **Open** `.md`/`.txt` files from other apps ("Open with" / "Share to" mdwriter).

## Privacy

- No permissions at all (the only entry in `aapt2 dump permissions` is the androidx signature-only receiver permission).
- No network: there is no INTERNET permission, and the preview renders offline.
- No accounts, no analytics, no crash reporting.

## Known limitations

- Per-keystroke latency on very large notes is above the original budgets on the emulator (100k characters: about
  12 ms median); opening a 300k-character note takes about 3.5 s. See `plans/perf-results.md`.
- Right-to-left paragraphs in a left-to-right column are offset by the gutter on wide screens.
- Notes over 5 MB open read-only; files over 16 MB and non-text files are refused.
- Several accessibility, large-screen, RTL and keyboard checks were never run on a real phone; see `plans/QA-matrix.md`
  (rows marked `user`).

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
