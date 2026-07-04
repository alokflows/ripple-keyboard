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

Upstream copyright headers and license text are unaltered; upstream source files
are modified only at the integration points listed above.

## Trademark / endorsement notice

"FlorisBoard" and its associated names, logos, and marks are the property of the
FlorisBoard project and its contributors. They are used here only to accurately
describe the origin of this fork. Neither the FlorisBoard name nor its marks are
used to endorse, promote, or imply any affiliation with this product. Ripple
Keyboard is an independent, unofficial fork and is not endorsed by or affiliated
with the FlorisBoard project.
