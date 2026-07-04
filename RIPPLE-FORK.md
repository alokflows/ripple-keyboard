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

No upstream source files, copyright headers, or license text have been removed
or altered.

## Trademark / endorsement notice

"FlorisBoard" and its associated names, logos, and marks are the property of the
FlorisBoard project and its contributors. They are used here only to accurately
describe the origin of this fork. Neither the FlorisBoard name nor its marks are
used to endorse, promote, or imply any affiliation with this product. Ripple
Keyboard is an independent, unofficial fork and is not endorsed by or affiliated
with the FlorisBoard project.
