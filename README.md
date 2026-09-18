# BlockTime

An Android app for blocking out time on your Google Calendar. Open a day, see what is already
booked, tap a free gap, and it becomes a calendar event — on your real Google Calendar, visible
to anyone you share it with.

Built with Kotlin, Jetpack Compose (Material 3) and the Google Calendar REST API v3.

## What it does

**Block time**
- Tap any free gap in the day, or the **Block time** button, to open the block sheet.
- The suggested start time is the next free slot inside your working hours, rounded to the
  quarter hour — not "right now", mid-meeting.
- One-tap presets (Deep work 90m, Focus 60m, Admin 30m, Break 15m) and duration chips.
- Pick the calendar, colour, reminder, and whether the block shows you as **busy** or **free**.
- Repeat daily, on weekdays, or weekly, with an optional end date.
- **Conflicts are shown before you save** — if the slot overlaps something, the sheet says what.

**View your calendar**
- Day view with a scrollable two-week date strip; swipe-free navigation via the arrows.
- Events and free gaps interleaved in one chronological list, so "what's on at 3pm" and
  "where can this fit" are the same glance.
- A summary bar with booked time, free time and event count for the day.
- Multiple calendars merged, with per-calendar filter chips.
- Tap any event for details; edit or delete the ones on calendars you can write to.

**Settings**
- Which calendars are shown, which one new blocks go to.
- Working hours (the planning window used for free-slot detection).
- Default block length, disconnect / manage Google access.

## Project layout

```
app/src/main/java/com/blocktime/
├── BlockTimeApp.kt          # manual DI container (Retrofit, OkHttp, repositories)
├── MainActivity.kt          # consent flow + navigation host
├── auth/                    # AuthorizationClient wrapper, token state
├── data/remote/             # Calendar API v3 interface, DTOs, auth interceptor
├── data/repository/         # domain mapping, error messages, DataStore settings
├── domain/                  # models + Scheduling (pure, unit-tested)
├── ui/day/                  # day screen, view model, rows, detail sheet
├── ui/block/                # block sheet, time/date pickers
├── ui/settings/, ui/signin/, ui/theme/, ui/common/
└── util/                    # date formatting, RFC 3339
```

The scheduling logic (free slots, conflicts, merged busy intervals, next-fit slot) lives in
`domain/Scheduling.kt` with no Android or network dependencies, and is covered by
`app/src/test/java/com/blocktime/domain/SchedulingTest.kt`.

## Setting up Google access (required before the app will run)

The app talks to Google as *your users*, using OAuth. Nothing works until you register the app
in a Google Cloud project. This is a one-time setup for you, not something partners repeat.

### 1. Create the Cloud project and enable the API

1. Go to <https://console.cloud.google.com/> → create a project (e.g. "BlockTime").
2. **APIs & Services → Library** → enable **Google Calendar API**.

### 2. Configure the OAuth consent screen

1. **APIs & Services → OAuth consent screen**.
2. If your partners are on your Google Workspace domain, choose **Internal** — no verification
   needed, and anyone in the domain can use it.
3. If they are on ordinary Gmail accounts (or other domains), choose **External** and leave the
   app in **Testing**. Add each partner's Google address under **Test users** (up to 100).
   Testing-mode tokens expire weekly, so users re-approve about once a week.
4. Add these scopes:
   - `https://www.googleapis.com/auth/calendar.readonly`
   - `https://www.googleapis.com/auth/calendar.events`

   Both are *sensitive* scopes. That is fine for Internal or Testing; only a public launch to
   unlimited users requires Google's verification review.

### 3. Create Android OAuth clients

**APIs & Services → Credentials → Create credentials → OAuth client ID → Android.**

You need one client per (package name, signing certificate) pair:

| Build | Package name | SHA-1 from |
|---|---|---|
| Debug | `com.blocktime.debug` | your debug keystore |
| Release | `com.blocktime` | your release keystore (or Play App Signing) |

Get the SHA-1 fingerprints:

```bash
# Debug keystore (created automatically by Android Studio)
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
  -storepass android -keypass android | grep SHA1

# Your release keystore
keytool -list -v -keystore /path/to/release.jks -alias <your-alias> | grep SHA1
```

If you distribute through Google Play, Play re-signs your app: take the SHA-1 from
**Play Console → your app → Setup → App integrity → App signing key certificate** and register
that one too, or sign-in will fail for Play-installed builds only.

There is no `google-services.json` and no API key in this project — Google matches the app by
package name plus signing certificate, so **nothing secret is committed to this repo**.

### 4. Build and run

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # scheduling unit tests
./gradlew lintDebug
```

Set `sdk.dir` in `local.properties` (Android Studio does this for you), or export `ANDROID_HOME`.
Devices need Google Play services — the app uses Play Services Auth for the OAuth flow.

## Giving the app to your business partners

Pick whichever fits how formal you want this to be.

**a) Send them the APK directly.** Simplest for a handful of people.
```bash
./gradlew assembleRelease      # needs keystore.properties, see below
```
Send `app/build/outputs/apk/release/app-release.apk`. They enable "install unknown apps" for
whatever app they receive it in. Every partner's Google address still has to be a Test user
(or in your Workspace domain) from step 2.

The included GitHub Actions workflow (`.github/workflows/android.yml`) builds a debug APK on
every push and attaches it to the run, so you can also just send them a link to the artifact.

**b) Google Play internal testing.** Up to 100 testers, installs and updates through the Play
Store like a normal app, no "unknown sources" prompt. Requires a Play Console account
(one-time $25). Upload an **AAB**:
```bash
./gradlew bundleRelease        # app/build/outputs/bundle/release/app-release.aab
```

**c) Workspace-managed distribution.** If everyone is on your Workspace domain, an Internal
OAuth app plus Play's private app publishing gives the cleanest experience: no test-user list,
no weekly re-consent.

### Signing a release build

Create `keystore.properties` in the repo root (it is git-ignored):

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`app/build.gradle.kts` picks it up automatically; without it, `assembleRelease` produces an
unsigned APK. Keep the keystore and this file out of version control — losing the keystore means
you can never update a Play-published app.

## Privacy notes

- The app stores **no calendar data on the device**. Every screen reads live from Google.
- The OAuth access token is held in memory only and is re-obtained silently at launch; it is
  never written to disk, and `disconnect` drops it immediately.
- Only your settings (chosen calendars, working hours, defaults) are persisted, via DataStore.
- Blocks created by the app are tagged with a private extended property (`createdBy`) so the app
  can tell its own events apart. It is invisible in Google Calendar's UI.

## Known limits

- Editing an occurrence of a repeating event updates that occurrence only; the app does not
  offer "change the whole series".
- Free/busy is computed from the calendars you have selected, not from other people's calendars.
  Blocking does not check whether your partners are free.
- No offline mode: without a connection, the day view shows the error and nothing else.
- Time zone follows the device; events created while travelling use the device zone.
