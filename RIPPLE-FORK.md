# Ripple Keyboard — Fork Notice

Ripple Keyboard is a fork of **FlorisBoard**, an open-source privacy-first
keyboard for Android.

- **Upstream project:** FlorisBoard — https://github.com/florisboard/florisboard
- **Pinned upstream release:** `v0.5.2` (latest stable at the time of forking)
- **License:** Apache License 2.0 (see [`LICENSE`](./LICENSE), retained unchanged
  from upstream)

## About this fork

Ripple Keyboard builds on FlorisBoard to add a privacy-first, cross-device text
feature (type on one device, land the text at the cursor on another). The goal
is to keep the upstream keyboard intact and layer Ripple-specific functionality
on top.

## Modifications

Modifications made in this fork relative to the pinned upstream release will be
listed here as they land. As of the initial foundation commit:

- Added `.github/workflows/build.yml` — a CI workflow (adapted from upstream's
  `.github/workflows/android.yml`) that builds a debug APK on the `ripple`
  branch.
- Added this `RIPPLE-FORK.md` attribution notice.
- Pinned the `patrickgold-jetpref` dependency to the released `0.3.0-rc01`
  instead of upstream v0.5.2's `20251119T222500Z-SNAPSHOT`. That snapshot has
  been purged from the Maven snapshots repository (snapshots expire), which made
  the tag unbuildable; `0.3.0-rc01` is the released jetpref version used by the
  next upstream release line (v0.6.0-alpha).

Slice 2 — minimal rebrand + the Ripple cross-device feature:

- **Rebrand (minimal-diff):** app label → "Ripple Keyboard" (`app_name` +
  debug/beta `floris_app_name` resValues), `applicationId` →
  `com.ripple.keyboard` (debug builds keep the `.debug` suffix). The Kotlin
  namespace stays `dev.patrickgold.florisboard` to keep the upstream diff thin;
  all manifest provider authorities use `${applicationId}` /
  `BuildConfig.APPLICATION_ID` and follow the new id automatically.
- **Ripple core** (new package `dev.patrickgold.florisboard.ripple`, ported from
  the Ripple product repo `alokflows/ripple`): `RippleCrypto` (AES-256-GCM,
  PBKDF2-210k, byte-compatible with Ripple's JS/Rust cores; cross-language
  vectors asserted by `RippleCryptoTest`, run by `./gradlew :app:testDebugUnitTest`
  and CI), `RippleClient` (one OkHttp WebSocket to the blind relay — only a
  SHA-256 room hash and sealed blobs ever leave the device), `RippleManager`
  (process-wide socket owner + `StateFlow`, persisted pairing code, registered
  as a lazy `FlorisApplication` manager), `RippleConnectionService` (foreground
  `dataSync` service keeping the socket alive). New manifest entries: INTERNET +
  FOREGROUND_SERVICE(_DATA_SYNC) permissions and the service. New dependency:
  OkHttp 4.12.0.
- **Ripple panel:** `ImeUiMode.RIPPLE`, `KeyCode.IME_UI_MODE_RIPPLE` (-214) +
  predefined key `ime_ui_mode_ripple`, switching in `KeyboardManager`, and
  `ripple/RippleInputLayout.kt` — connect view (enter pairing code), received
  messages as chips that insert at the cursor on tap, and a compose-and-send
  row. Reached via a Smartbar quick action (default arrangement, Devices icon).
  Panel elements are registered Snygg theme elements (`ripple-*`), so any theme
  can style them.
- **CI:** the build workflow also runs the JVM unit tests (crypto vectors
  included).

Slice 3 — Ripple settings screen + consent mode:

- **Settings screen** (new package `dev.patrickgold.florisboard.app.settings.ripple`,
  `RippleScreen.kt`): a "Ripple" section in FlorisBoard's Settings app, built with
  the upstream `FlorisScreen` + jetpref preference composables. It shows the
  connection status and the current pairing code (masked as dots, with a show/hide
  toggle and a copy-to-clipboard action), a "Change code" action (a
  `JetPrefAlertDialog` + `JetPrefTextField` that re-pairs), and a "Disconnect"
  action. The pairing code is **not** duplicated into jetpref — the screen reads
  and mutates it through the existing `RippleManager` (`savedCode`, `connect`,
  `leave`). Registered like every other screen: a `Routes.Settings.Ripple`
  deeplink (`settings/ripple`), an entry in `Routes.AppNavHost`, and a `Preference`
  row (Devices icon) on `HomeScreen`.
- **Consent mode** (new `ripple.ConsentMode` enum: `AUTO` / `ASK` / `OFF`, default
  `ASK`) governs incoming text from a paired device. Exact semantics:
  - `AUTO` — an incoming message is committed at the cursor immediately **while the
    keyboard is visible**, and is also kept in the panel as a chip so a message
    that arrives while the keyboard is hidden is not lost.
  - `ASK` — the previous (default) behavior: messages wait in the panel as
    tap-to-insert chips; nothing is inserted without a tap.
  - `OFF` — the panel receives nothing: incoming messages and received history are
    dropped at ingest in `RippleManager` (only the user's own pending sends are
    kept). Sending still works.
  The `AUTO` / `OFF` decisions are pure data on the enum (`receivesIntoPanel`,
  `autoCommits`), enforced in `RippleManager.onEvent` at ingest. `AUTO` never
  blocks a keypress — it reacts to already-decrypted messages via a process-wide
  `RippleManager.autoCommits` `SharedFlow`, collected by `FlorisImeService` (guarded
  by `isWindowShown`), off the network path.
- **Prefs** (`FlorisPreferenceModel.Ripple` group): `ripple__consent_mode` (enum,
  default `ASK`) and `ripple__keep_connection_alive` (boolean, default `true`).
  Enum labels/descriptions wired through `EnumDisplayEntries`. The keep-alive
  toggle gates the foreground `RippleConnectionService`: `RippleManager.connect`
  only starts it when the pref is on, and `RippleManager` observes the pref to
  start/stop the service live while connected.
- **Tests:** `RippleConsentModeTest` pins the consent contract (receive/commit
  matrix per mode, `ASK` default, exactly one auto-committing mode) as JVM unit
  tests; run by `./gradlew :app:testDebugUnitTest` and CI.
- **CI:** the build workflow trigger now also fires on `slice-*` push branches.
- **Strings:** default-locale `settings__ripple__*`, `pref__ripple__*`, and
  `enum__consent_mode__*` resources added following upstream naming.
- **Fix (panel):** the expanded-compose inline key grid rendered blank key labels
  (touch targets worked, only the backspace icon painted) because `SnyggButton`'s
  built-in Material content padding (8dp top + bottom) left less than one text
  line of content height in the ~25dp rows that layout gives each grid row; the
  grid keys are now padding-free clickable `SnyggBox`es (same `ripple-key` theme
  element, centered content) in `RippleInputLayout.InlineKeyGrid`.

Upstream copyright headers and license text are unaltered; upstream source files
are modified only at the integration points listed above.

## Trademark / endorsement notice

"FlorisBoard" and its associated names, logos, and marks are the property of the
FlorisBoard project and its contributors. They are used here only to accurately
describe the origin of this fork. Neither the FlorisBoard name nor its marks are
used to endorse, promote, or imply any affiliation with this product. Ripple
Keyboard is an independent, unofficial fork and is not endorsed by or affiliated
with the FlorisBoard project.
