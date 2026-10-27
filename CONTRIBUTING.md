# Contributing to Tellyscope

Thanks for helping! Tellyscope is young, so the code and its structure are still moving.

## Ground rules

- **Read the [safety rules](README.md#safety-rules) first.** A pull request that uninstalls apps that came with the TV, bypasses the protected list or skips recording what was already off won't be merged.
- **Open pull requests against `dev`.** `main` is the release branch and only takes `dev`.
- **One change per pull request.** Smaller is easier to review.
- **`./gradlew build` must pass** before you open a PR. CI runs the same command.
- **New files start with an SPDX header:** `// SPDX-License-Identifier: GPL-3.0-only`.
- **Licensing.** By contributing you agree that your work is licensed under GPL-3.0. If you adapt code or lists from another project, check that its license is compatible and add it to [NOTICE](NOTICE) in the same PR.

## Releases

- **Alpha:** to publish an alpha, put `[release]` anywhere in the title of the pull request into `dev` (any case), for example `Fix the remote [release]`. Merges without it only run CI. On a `[release]` merge, CI builds `app.tellyscope.alpha`, which installs next to the release, and once the build passes it tags the commit `vX.Y.Z-alpha.N` and publishes a GitHub pre-release under that tag, with the APK named `Tellyscope-vX.Y.Z-alpha.N.apk`. `X.Y.Z` is the current `versionName`, and `N` is one past the highest alpha of that version. A failed build tags nothing. You can still put out an alpha by hand: in Actions, open **Release**, choose **Run workflow** on `dev`, and it publishes the next `vX.Y.Z-alpha.N` from dev's head the same way. A run on any other branch fails and publishes nothing. Pushed tags no longer start a release.
- **Shipping:** a release is a pull request from `dev` into `main`, merged with a merge commit. It must raise `versionName` and `versionCode` in `app/build.gradle.kts` past the latest `v*` tag and turn the `## Unreleased` section of [CHANGELOG.md](CHANGELOG.md) into that version's section; CI refuses anything else. Merging it tags `vX.Y.Z` and publishes a GitHub release with the signed APK, notes from the changelog section and the commits since the previous tag.
- **Signing:** alphas and releases are signed with two different keys, so an alpha can never install over the release app. Each key lives in its own GitHub Environment: `ALPHA_KEYSTORE_BASE64`, `ALPHA_KEY_ALIAS` and `ALPHA_KEY_PASSWORD` in `alpha`, which deploys only from `dev`; `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` in `release`, which deploys only from `main`. `*_KEYSTORE_BASE64` is the keystore, base64-encoded, and `*_KEY_PASSWORD` is both the store and the key password (keytool's PKCS12 default uses one for both). A job with its secrets missing fails; it never falls back to the other key. Only the maintainer holds the keys; never commit a keystore. Local release builds are unsigned.

## Debloat lists

The per-TV lists behind **Debloat** and the Overview's list card live in the [tellyscope-devices](../../../tellyscope-devices) repository, not here; add or fix a list there. When you add a package to a list:

- Say which TV models you tested on, and what changed after disabling it.
- If disabling it broke anything (launcher, updates, remote, input, HDMI-CEC), put it in the list's keep entries, or propose it for the built-in protected list (`adb/src/main/kotlin/app/tellyscope/adb/Protected.kt`) here.

### Using your own lists

You don't have to trust ours. Fork tellyscope-devices, edit the lists in your fork, and keep `index.json` on schema 1 with each file's sha256 updated. Then in the app open **Settings** (in the ⋮ menu at the top right), open **Advanced** at the bottom, tap **Use your own debloat lists**, and paste the raw address of the folder that holds `index.json`, for example `https://raw.githubusercontent.com/<you>/tellyscope-devices/main/`. Only HTTPS is accepted, and a source that doesn't serve a schema 1 index is refused without changing anything. Switching drops the cached lists. While your source is in use, the review step says where the list came from. The built-in protected list still wins over any list, and **Reset to default** goes back to tellyscope-devices.

## Launchers

[`app/src/main/assets/launchers.json`](app/src/main/assets/launchers.json) lists the third-party launchers Tellyscope knows. Set `"accessibility": true` only for a launcher that keeps Home with its own accessibility service: Set as default offers to turn that service on, and an accessibility service can read the screen. Check the APK's manifest for a service with `BIND_ACCESSIBILITY_SERVICE`, and put the version you checked in `"checked"`. The list ships with the app and is never fetched, so a custom list source can't add to it.

## README screenshots

They come from the screenshot tests, so they always show the current UI with sample data. To refresh them, render the tests, then put them in phone frames (needs Python 3 with Pillow):

```sh
./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest' -Proborazzi.test.record=true
python3 docs/screenshots/frame.py
```

`frame.py` lists which test image becomes which README image. Keep the README at 12 images or fewer.

## Style

- Kotlin official code style, as set up in `.editorconfig`.
- Logic that doesn't need Android goes in `:adb`, with unit tests.
- UI uses Material 3 Expressive components and the app theme. No hard-coded colors.

## Reporting a TV

Open a [device report](../../issues/new?template=device_report.yml). Leave out serial numbers, IP addresses and account names.
