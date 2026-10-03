#!/usr/bin/env bash
# Creates YOUR signing key and prints the GitHub secrets to store. Run this on your own PC, never in CI.
# Keep parasite-release.jks and the passwords somewhere safe (password manager + offline backup):
# if you lose the key you cannot update the installed app; if someone steals it they can ship fake updates.
set -euo pipefail
KS=parasite-release.jks
ALIAS=parasite
[ -e "$KS" ] && { echo "$KS already exists - refusing to overwrite"; exit 1; }
read -r -s -p "Choose a keystore password (also used for the key): " PW; echo
keytool -genkeypair -v -keystore "$KS" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PW" -keypass "$PW" -dname "CN=Parasite, O=Personal"
echo
echo "Now add these as repository secrets (Settings > Secrets and variables > Actions), or with the gh CLI:"
echo "  base64 -w0 $KS | gh secret set ANDROID_KEYSTORE_BASE64"
echo "  printf '%s' '<your password>' | gh secret set ANDROID_KEYSTORE_PASSWORD"
echo "  printf '%s' '$ALIAS' | gh secret set ANDROID_KEY_ALIAS"
echo
echo "Then delete nothing: keep $KS backed up OFF this machine. Do not commit it (it is in .gitignore)."
