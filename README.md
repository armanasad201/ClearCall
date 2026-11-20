# ClearCall

A private, on-device call recorder for Android, built with Kotlin and Jetpack Compose. No account, no cloud, no ads. The app declares **no INTERNET permission**, so recordings cannot leave the phone unless you share, export or back them up yourself.

## Read this first: what Android allows

Since Android 10, the system stops regular apps from capturing the audio of a phone call. Only software installed by the phone maker, holding the privileged `CAPTURE_AUDIO_OUTPUT` permission, can record both sides. A normal app cannot grant itself that permission, and ClearCall does not use workarounds such as root or accessibility tricks.

ClearCall therefore reports truthfully what it can do on each phone and never fakes a recording:

| Situation | What ClearCall does |
|---|---|
| Device grants call-audio capture (privileged/OEM build) | Records both sides from the telephony stream. Labelled **Both sides**. |
| Regular install (almost every phone) | Records through the **microphone**. Your voice is captured clearly; the other person only if audible (speakerphone). Labelled **Microphone only**. |
| Microphone mode switched off | Does not record. Dashboard says so. |
| Phone silences the mic during calls (common on Android 10+) | The recording is saved but flagged **No audio detected** (peak-level check), and a notification says so. |
| No telephony hardware (tablet, Wi-Fi only) | Says there are no calls to record. |

The **Device compatibility** screen (Settings) explains this in plain language for the phone in your hand.

## Features

- **Recording**: incoming and outgoing calls; modes Off / Ask / Auto. *Ask* posts a "Record" notification and captures nothing until you tap it. *Auto* starts by itself where Android allows and otherwise falls back to the Record button (Android 12+ restricts starting microphone services from the background).
- **Quality**: AAC 128 kbps (44.1 kHz), AAC 256 kbps (48 kHz), or lossless WAV (16-bit PCM, 48 kHz). If the encoder refuses a configuration the recorder steps down to a safer sample rate and bitrate rather than failing.
- **Consent and disclosure**: onboarding requires accepting a plain-language recording-and-the-law notice (also re-readable in Settings). A persistent notification with a timer is shown during every recording. ClearCall does **not** announce itself to the other party; the notice says so and tells the user to obtain consent.
- **Library**: grouped by day, searchable by name or number (digits-only matching), filter by incoming / outgoing / starred, sort by newest / oldest / longest.
- **Player**: Media3 ExoPlayer with play, pause, replay, seek, ±10 s and speed 0.5x to 2x. Auto-pauses on headphone unplug and when the app is backgrounded.
- **Storage**: rename, star, delete (confirmed), share (FileProvider, read-only grant), export via the system file picker, ZIP backup and restore, optional auto-delete of old unstarred recordings.
- **Privacy**: optional app lock (fingerprint/face with device PIN fallback), `FLAG_SECURE` while locked-on, in-memory unlock state with a 15 s grace period, notification text never contains names or numbers, `allowBackup="false"`.
- **UI**: Material 3, light/dark/system theme, animated transitions, dashboard with live status and recent recordings.

## Build and run

Requirements: Android Studio Ladybug (2024.2.1) or newer, JDK 17, Android SDK Platform 35.

1. Open the `ClearCall` folder in Android Studio and let Gradle sync.
2. Run on a **physical phone** with a SIM. Emulators cannot place real calls, so recording cannot be exercised there.
3. Command line: `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`.

`minSdk 26` (Android 8.0), `targetSdk` and `compileSdk` 35.

## Tests

`app/src/test` holds JVM unit tests for the pure logic: duration/size/date formatting, the WAV header writer and repair, backup-path safety (untrusted ZIP names), the app-lock grace logic, title fallbacks and the capability messages.

## Architecture

```
app.clearcall
├── AppContainer / ClearCallApp   hand-rolled DI, one container per process
├── core/        permissions helpers, formatting
├── data/        Room (recordings), DataStore (settings), repository, ZIP backup/restore
├── recording/   CallStateReceiver -> RecordingService (foreground, type microphone)
│                engines: MediaRecorder (AAC/m4a) and AudioRecord (WAV)
│                capability detection, notifications, call-log/contact lookup
├── security/    biometric prompt, in-memory lock state
└── ui/          Compose screens + ViewModels: home, library, player, settings,
                 onboarding, lock, device check, navigation, theme
```

Recording flow: `PHONE_STATE` broadcast -> receiver reads the user's mode and consent -> service goes foreground within the platform deadline -> tries each available audio source in order (call audio if privileged, then mic, then voice-recognition mic; at several sample rates) -> on stop, finalises the file, measures the signal, saves metadata, then enriches it from the call log and contacts when those optional permissions are granted. A recording orphaned by a killed process is recovered on next launch.

## Permissions

| Permission | Why | Required |
|---|---|---|
| `RECORD_AUDIO` | Capture audio | Yes |
| `READ_PHONE_STATE` | Detect call start/end | Yes |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Keep recording while the app is closed | Yes |
| `POST_NOTIFICATIONS` (Android 13+) | Recording indicator and Record button | Optional |
| `READ_CALL_LOG` | Who the call was with, and direction | Optional |
| `READ_CONTACTS` | Show names instead of numbers | Optional |
| `USE_BIOMETRIC` | App lock | Optional |
| `CAPTURE_AUDIO_OUTPUT` | Declared so privileged builds can use it; never granted to regular installs | No |

## Verification status (please read)

This project was written without an Android SDK or a physical device available, so:

- **Verified here**: all five unit-test classes (24 tests) compile and pass against the real source; every Kotlin file parses with no syntax errors under the Kotlin 2.0.21 compiler; no capitalised type is used without an import; every `by` delegate has its import; the manifest and all resources are well-formed and every referenced class and resource exists.
- **Reviewed by reading, not compiling**: calls into Compose, Material3, Navigation, Media3, Room and Biometric APIs. An independent review by separate reviewers was attempted twice but cut off by a usage limit before any findings came back, so the review was done by the author instead. It found and fixed: AAC encoder fallback that never lowered the bitrate, a live recording notification overwritten by a duplicate start, a stale call state that could skip the next call, and an audio-recorder leak when the output file could not be opened.
- **Not verified here**: a full Gradle build against the Android SDK, and any run on a device or emulator. Expect to fix a small number of compile errors on first sync, and treat an independent review of the Compose and recording code as still worth doing.
- **Compatibility matrix below is expected behaviour, not measured results.** Run the checklist on real phones before relying on it.

### Device test checklist

| Android | What to confirm |
|---|---|
| 8-9 (API 26-28) | Mic recording during a call; Auto starts from the broadcast; notifications show the "Active recording" channel |
| 10 (API 29) | Whether the mic is silenced during calls; "No audio detected" flag appears if so |
| 11-12 (API 30-31) | Auto falls back to the Record button when the background start is refused |
| 13 (API 33) | Notification permission prompt; Record button appears after granting |
| 14-15 (API 34-35) | Microphone foreground-service type accepted; tapping Record works; edge-to-edge insets look right |

On each: incoming and outgoing call, speakerphone on and off, all three quality settings, rotate during playback, lock/unlock, backup then restore on a fresh install, share to another app, deny each permission in turn and check the messages, and a Samsung, Xiaomi and Pixel device at minimum, because vendors differ.

## Known limitations

- Both-sides recording is only possible on devices that grant call-audio capture (see top).
- Backup ZIPs are not encrypted; keep them somewhere you trust.
- After the 15 s lock grace period the app returns to the dashboard rather than the previous screen.
- Outgoing numbers are read from the call log after the call, so they appear only if `READ_CALL_LOG` was granted.
- No dual-SIM labelling, transcription or cloud sync (cloud sync is deliberately absent).
