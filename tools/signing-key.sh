#!/usr/bin/env bash
# Creates the release signing key for BlockTime and prints everything needed to
# register it with Google and with GitHub Actions.
#
# Run this ONCE, on a machine you control. Keep the resulting .jks file backed up:
# lose it and you can never update an already-installed app under the same identity.
set -euo pipefail

KEYSTORE="${1:-blocktime-release.jks}"
ALIAS="${ALIAS:-blocktime}"

command -v keytool >/dev/null || {
    echo "keytool not found. Install a JDK (e.g. 'brew install openjdk' or 'apt install default-jdk')." >&2
    exit 1
}

if [ -e "$KEYSTORE" ]; then
    echo "Refusing to overwrite the existing keystore: $KEYSTORE" >&2
    echo "Back it up and delete it first, or pass a different filename." >&2
    exit 1
fi

read -r -s -p "Choose a keystore password (at least 6 characters): " STOREPASS; echo
read -r -s -p "Confirm the password: " CONFIRM; echo
[ "$STOREPASS" = "$CONFIRM" ] || { echo "Passwords do not match." >&2; exit 1; }
[ "${#STOREPASS}" -ge 6 ] || { echo "Password must be at least 6 characters." >&2; exit 1; }

# :env keeps the password out of the process list, unlike -storepass <literal>.
export BT_STOREPASS="$STOREPASS"

keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=BlockTime, OU=Mobile, O=BlockTime, C=IN" \
    -storepass:env BT_STOREPASS \
    -keypass:env BT_STOREPASS

SHA1="$(keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass:env BT_STOREPASS \
    | grep -m1 'SHA1:' | sed 's/.*SHA1: *//')"

base64 -w0 "$KEYSTORE" > "$KEYSTORE.base64" 2>/dev/null \
    || base64 "$KEYSTORE" | tr -d '\n' > "$KEYSTORE.base64"

unset BT_STOREPASS

cat <<REPORT

──────────────────────────────────────────────────────────────
Created: $KEYSTORE

1. Register this pair in Google Cloud Console
   Credentials -> Create credentials -> OAuth client ID -> Android

   Package name : com.blocktime
   SHA-1        : $SHA1

2. Add four GitHub repository secrets
   Settings -> Secrets and variables -> Actions -> New repository secret

   RELEASE_KEYSTORE_BASE64    contents of $KEYSTORE.base64
   RELEASE_KEYSTORE_PASSWORD  the password you just chose
   RELEASE_KEY_ALIAS          $ALIAS
   RELEASE_KEY_PASSWORD       the same password

3. Delete $KEYSTORE.base64 once the secret is saved, and keep
   $KEYSTORE itself backed up somewhere safe and out of git.
──────────────────────────────────────────────────────────────
REPORT
