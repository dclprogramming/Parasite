# Security review and changes

## Background
SmartTube's maintainer disclosed that his APK signing key was exposed and used to ship tampered update
packages; compromised builds contained a hidden native library (`libalphasdk.so`) not present in the source.
Whether the maintainer was only a victim cannot be decided from outside, so this fork assumes nothing and
removes the things that made the incident possible, then adds checks that would catch a repeat.

## What I checked in the source (snapshot in UPSTREAM.md; history was not reviewed)
* No `libalphasdk`, no custom C/C++ code, no `DexClassLoader`/`PathClassLoader`/`Runtime.exec`.
* The only prebuilt native code in the tree is `SharedModules/j2v8/.../libj2v8.so` (4 ABIs). `strings` shows
  only V8/J2V8 symbols and an Android NDK r19-era compiler banner; no URLs, no non-J2V8 JNI exports.
  I could not rebuild or compare it with a trusted J2V8 build, so treat it as the one unverified binary.
* Hosts referenced in main code besides YouTube/Google: SponsorBlock and DeArrow (`*.ajay.app`),
  `api.qrserver.com` (QR codes), flag image CDNs, the author's GitHub release URLs (removed, below),
  donation/chat links in the About screen (removed).
* Permissions are modest (internet, microphone, notifications, storage, overlay, boot).

## Removed / disabled
| Item | Why |
|---|---|
| Self-updater (`AppUpdatePresenter.start`, menu/About buttons, `update_urls`) | Downloaded and installed APKs from the author's GitHub: the exact channel abused. |
| "Bridge" and "Restore stable" installers (`BridgePresenter` and subclasses) | Downloaded and installed other APKs. URLs blanked, installer is a no-op. |
| `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES` | Stripped from the merged manifest (`tools:node="remove"`). The app cannot install packages. |
| Firebase Crashlytics + Google Services plugin + `google-services.json` | Telemetry to the author's Firebase project (beta/stable flavors). |
| Hard-coded master password `smarttube` in `Utils.passwordMatch` | Bypassed the user's own PIN/lock. |
| Donation/feedback/link entries in About | Pointed at third-party pages; unnecessary. |
| Remote `constants.json` (`ConstantsService`) | Upstream downloaded OAuth client id/secret and an API key at runtime from the original author's GitHub release, so whoever controls that file controls the config. Now nothing is fetched; Google-Drive backup sign-in and YouTube Data API v3 features stay off (normal YouTube sign-in and playback don't use them). |
| PO-token "cloud" servers (`service1.com`, `service2.com`) | Third-party hosts that received the visitor id when on-device PO-token generation was unavailable. List emptied. |
| Unofficial `gradle-wrapper.jar` files | Gradle's wrapper validation rejected the app's wrapper jar (a pre-release Gradle 1.6 nightly from 2013) and the one under `exoplayer-amzn-2.10.6` (a 2.2.1 release candidate). Both look like old leftovers rather than tampering, but this can't be proven, so the root jar was replaced with the official Gradle 7.5 one (taken from Gradle's own v7.5.0 tag, sha256 `91a23940...b1e60`) and the unused ExoPlayer wrapper was deleted. |
| `jcenter()` repository | Deprecated mirror; one more place a dependency could be swapped. Maven Central/Google/JitPack remain. |

## Added
* `.github/workflows/build.yml`: builds `stfdroid` only, actions pinned by commit SHA, Gradle wrapper
  validated against Gradle's official checksums, signs with your key from secrets, removes the key afterwards.
* `tools/verify_apk.py`: fails the build on denied permissions, indicators of compromise
  (`alphasdk`/`libalpha` strings or files) and (after you record `tools/baseline.json`) on any new
  permission, native library or network host.
* `tools/make_keystore.sh`: creates your own signing key.
* New application id `app.parasite.tv` (search provider authority updated), so nothing collides with upstream.

## Still fetched at runtime (accepted)
* SponsorBlock / DeArrow (`*.ajay.app`), Return YouTube Dislike, `api.qrserver.com` (QR codes), flag/placeholder image hosts: optional features, no code is executed from them.
* The YouTube challenge solver script from `github.com/yt-dlp/ejs` release 0.0.1, only if the bundled/cached copy is missing; it runs inside the J2V8 JavaScript engine.

## Unverified / still to do
* Nothing has been compiled. Expect build errors to fix from the Actions logs.
* The j2v8 binaries (above) and JitPack dependencies (`webpdecoder`, `florianingerl regex`) are trusted as-is.
  Once the build is green, consider replacing j2v8 with a build you make yourself.
* Upstream source was reviewed with targeted searches only, not line by line (about 400k lines).
* Behaviour changes (updater, bridge, PIN bypass) are untested on a device.
* The `stbeta`/`ststable` flavors still exist in Gradle but are never built or published here.
