# Getting BlockTime onto a phone

The app is finished and builds cleanly, but it cannot be installed as-is from this repo alone.
Two things have to happen first, and both are one-time:

1. **The APK must be signed with a key that stays the same forever.**
2. **That key's SHA-1 must be registered with Google**, or sign-in fails with "developer error"
   even though the app installs fine.

This is Google's rule for any app touching Calendar, not something specific to this project.

---

## Step 1 — Create the signing key (once, on a computer)

You need a machine with a JDK installed (`keytool` comes with it). Clone the repo and run:

```bash
./tools/signing-key.sh
```

It asks for a password, creates `blocktime-release.jks`, and prints:

- the **SHA-1 fingerprint** — for step 2
- a base64 copy of the key — for step 3

**Back up the `.jks` file and the password.** If you lose them, you cannot ship updates that
install over the existing app; everyone would have to uninstall and reinstall.

## Step 2 — Register the app with Google (once, in a browser)

1. <https://console.cloud.google.com/> → create a project.
2. **APIs & Services → Library** → enable **Google Calendar API**.
3. **OAuth consent screen**:
   - Everyone on your Google Workspace domain → choose **Internal**. Nothing else to do.
   - Ordinary Gmail accounts → choose **External**, leave it in **Testing**, and add each
     person's Google address under **Test users**. They re-approve roughly weekly.
   - Add scopes `calendar.readonly` and `calendar.events`.
4. **Credentials → Create credentials → OAuth client ID → Android**:
   - Package name: `com.blocktime`
   - SHA-1: the fingerprint from step 1

No file from this step goes into the repo — Google identifies the app by package name plus
signing fingerprint, so there is no secret to commit.

## Step 3 — Build the APK

**If you have Android Studio:** open the project, plug the phone in with USB debugging on, and
press Run. Done — skip the rest.

**From the command line:**

```bash
cp /path/to/blocktime-release.jks .
cat > keystore.properties <<'EOF'
storeFile=blocktime-release.jks
storePassword=<your password>
keyAlias=blocktime
keyPassword=<your password>
EOF

./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Install over USB with `adb install -r app/build/outputs/apk/release/app-release.apk`, or just
email the APK to yourself and open it on the phone.

**From GitHub Actions (no computer needed after the first setup):** add four repository secrets
under **Settings → Secrets and variables → Actions**, using the values the script printed:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | contents of `blocktime-release.jks.base64` |
| `RELEASE_KEYSTORE_PASSWORD` | your password |
| `RELEASE_KEY_ALIAS` | `blocktime` |
| `RELEASE_KEY_PASSWORD` | your password |

Every push then produces a signed `blocktime-apk` artifact on the workflow run, downloadable
from the phone's browser. Delete the `.base64` file once the secret is saved.

## Step 4 — Install on the phone

1. Download or transfer `app-release.apk` to the phone.
2. Open it. Android asks to allow installs from whichever app delivered it — allow it once.
3. Play Protect may warn that the app is from an unknown developer. "Install anyway" — this is
   normal for any app not distributed through the Play Store.
4. Open BlockTime, tap **Connect Google Calendar**, pick the Google account, grant access.

If you get **"developer error"** or the consent screen closes immediately, the SHA-1 registered
in step 2 does not match the key that signed the APK. Check them against each other:

```bash
apksigner verify --print-certs app-release.apk    # SHA-1 of the installed APK
```

## Giving it to your partners

Once steps 1–3 are done, each partner needs only:

- their Google address added as a **Test user** (skip if they're on your Workspace domain), and
- the APK file, or a link to the GitHub Actions artifact.

They do **not** need the keystore, the Cloud project, or anything else.

For more than a handful of people, Play **internal testing** is worth the one-time $25: installs
and updates behave like a normal Play app, with no unknown-sources prompt. Build the upload file
with `./gradlew bundleRelease` and note that Play re-signs the app — register Play's signing
SHA-1 (Play Console → Setup → App integrity) as a second Android OAuth client, or Play-installed
copies will fail sign-in while your sideloaded ones work.
