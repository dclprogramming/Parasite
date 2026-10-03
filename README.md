# Parasite (hardened, restyled fork of SmartTube)

A YouTube client for Android TV / Google TV, forked from [SmartTube](https://github.com/yuliskov/SmartTube)
(MIT licence, see `LICENSE`) after the original project's signing key and release builds were compromised.
Everything needed to build is in this repository (the two former git submodules, `MediaServiceCore` and
`SharedModules`, are vendored as plain folders), so nothing is fetched from the original author's repos.

What is different, and why: see **SECURITY_CHANGES.md**. Pinned upstream versions: **UPSTREAM.md**.

## One-time setup

1. **Create a signing key on your own PC** (never in CI, never committed):
   `./tools/make_keystore.sh` (needs `keytool`, part of any JDK). Back up `parasite-release.jks` and the
   password somewhere safe and offline. Whoever holds this key can publish updates your TV will accept.
2. **Add three repository secrets** (GitHub repo > Settings > Secrets and variables > Actions):
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (the script prints the commands).
3. **Push to `main`.** The *Build Parasite APK* workflow builds `stfdroid` release, runs
   `tools/verify_apk.py`, and uploads the APK + `SHA256SUMS.txt` as an artifact.
   Push a tag (`git tag v1.0.0 && git push origin v1.0.0`) to also create a GitHub Release.
4. **Record a baseline once the first build is green.** Download the artifact, then run
   `python3 tools/verify_apk.py Parasite_*.apk --write-baseline`, read `tools/baseline.json`
   (permissions, native `.so` files, hosts the code references) and commit it. From then on any build that
   adds a permission, native library or network host fails the check.

## Installing on the TV

Download the APK from the Actions artifact / Release on a PC, compare its SHA-256 with `SHA256SUMS.txt`,
then sideload it (e.g. `adb connect <tv-ip>` then `adb install Parasite_fdroid_*.apk`). Package name is
`app.parasite.tv`, so it installs next to, and never over, the original SmartTube. Uninstall any old SmartTube
build you installed before the compromise was disclosed.

## Updating

There is no in-app updater, by design. To update: change the code, push a new tag, install the new APK
(same key = it upgrades in place). To pull in upstream fixes, diff the new upstream commits against the
hashes in UPSTREAM.md and **read the diff** before merging; the YouTube-breaking fixes usually land in
`MediaServiceCore`.

## Restyling

Colours: `common/src/main/res/values/colors.xml` and `smarttubetv/src/main/res/values/colors.xml`
(palette "Nebula"; colour *names* are unchanged from upstream, e.g. `semi_red` is the brand accent).
Icons/banner/logos: `smarttubetv/src/main/res/mipmap-nodpi/app_*.png` (same file names as upstream).
App name: `smarttubetv/src/main/res/values/strings.xml` and `src/stfdroid/res/values/strings.xml`.

## Not tested

Nothing here has been compiled or run (the author's environment has no Android toolchain). Expect to paste
GitHub Actions logs back to fix build errors. See SECURITY_CHANGES.md > "Unverified".
