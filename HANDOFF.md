# Handoff: BlockTime Android app

Context for a fresh Claude session (or any developer) picking this project up. Written at
commit `c5e7bec` on branch `claude/google-calendar-blocker-android-anm2fg`.

## What this is

An Android app that blocks out time on Google Calendar for daily tasks, and shows what is
already booked on a given day. Built for the repo owner and a handful of business partners,
all on Android phones, all signing in with their own Google accounts.

**The repository was deliberately emptied first.** It previously held an unrelated Next.js
site; the owner asked for a fresh start. That site still exists on `main` — this branch
replaces it wholesale. Do not try to reconcile the two.

## Current state

Everything builds and passes. Verified on this branch:

```
./gradlew assembleDebug      # OK
./gradlew assembleRelease    # OK, R8 + resource shrinking enabled
./gradlew testDebugUnitTest  # OK, 21 tests
./gradlew lintDebug          # OK, no warnings
```

27 Kotlin files, ~3,550 lines. A signed release APK is checked in at `dist/BlockTime.apk`
because the owner has no computer and needed a direct download link; it is superseded as soon
as CI signing secrets exist (see Outstanding work).

## Build environment

- JDK 17 (the build sets `jvmTarget = 17`; JDK 21 also works to run Gradle)
- Android SDK with platform 35 and build-tools 35.0.0
- `local.properties` with `sdk.dir=/path/to/Android/sdk` (git-ignored; Android Studio writes it)
- compileSdk/targetSdk 35, minSdk 26

minSdk 26 is deliberate: it makes `java.time` available natively, so there is no core-library
desugaring in the build. Do not lower it without adding desugaring back.

## Architecture

```
app/src/main/java/com/blocktime/
├── BlockTimeApp.kt          Application; manual DI container (OkHttp, Retrofit, repositories)
├── MainActivity.kt          Consent-intent plumbing + NavHost (day, settings)
├── auth/
│   ├── GoogleAuthorizer.kt  Play Services AuthorizationClient wrapper
│   └── AuthRepository.kt    Auth state machine + TokenProvider implementation
├── data/remote/
│   ├── CalendarApi.kt       Retrofit interface, Calendar API v3
│   ├── Dto.kt               Wire types (kotlinx.serialization)
│   └── AuthInterceptor.kt   Bearer token injection, one retry on 401
├── data/repository/
│   ├── CalendarRepository.kt  DTO↔domain mapping, HTTP error → user-facing message
│   └── SettingsStore.kt       DataStore preferences
├── domain/
│   ├── Models.kt            CalendarEvent, CalendarInfo, BlockDraft, Repeat, colours
│   └── Scheduling.kt        Free slots, conflicts, merged busy intervals, next-fit
├── ui/day/                  Day screen + view model, date strip, rows, detail sheet
├── ui/block/                Block sheet, time/date picker dialogs
├── ui/settings/, ui/signin/, ui/theme/, ui/common/
└── util/DateTimeFormat.kt   Formatting + RFC 3339
```

`domain/Scheduling.kt` is pure Kotlin with no Android or network imports, which is what makes
it testable. All 21 tests live in `app/src/test/java/com/blocktime/domain/`. Any change to
free-slot or conflict behaviour belongs there, with a test.

## Decisions already made (please don't relitigate without reason)

| Decision | Why |
|---|---|
| `AuthorizationClient` (Identity API), not `GoogleSignIn` | GoogleSignIn is deprecated; the app needs an access token, not an identity |
| Calendar REST API via Retrofit, not the Google API Java client | The Java client is heavy and awkward on Android; the REST surface used here is small |
| Manual DI container, not Hilt | The graph is a handful of objects; Hilt would add build complexity for no gain |
| Access token in memory only, never persisted | Tokens are short-lived and silently re-obtainable; nothing sensitive touches disk |
| No local caching of calendar data | Keeps correctness simple; the tradeoff is no offline mode |
| `singleEvents=true` on event queries | Expands recurrences into occurrences, which is what a day view needs |

Blocks created by the app carry a private extended property `createdBy=blocktime-android`, so
the app can recognise its own events. It is invisible in Google Calendar's UI.

## Google setup (owner's task, may still be incomplete)

The app cannot reach Calendar until an OAuth client exists. As of this handoff the owner was
working through it. Required, once, in Google Cloud Console (now under "Google Auth Platform"):

1. Project + enable Google Calendar API
2. Consent screen: **External**, left in **Testing** (partners use ordinary Gmail accounts)
3. Scopes: `calendar.readonly` and `calendar.events`
4. Test users: the owner's Gmail plus each partner's (required, or sign-in is blocked)
5. OAuth client, type **Android**:
   - Package name `com.blocktime`
   - SHA-1 `E0:0F:26:E3:9A:8A:8E:C2:1E:19:D8:14:43:F0:E3:F7:81:CC:A6:72`

There is no API key and no `google-services.json`. Google identifies the app by package name
plus signing certificate, so nothing secret belongs in this repo.

**Debug builds need their own OAuth client.** `applicationIdSuffix = ".debug"` means a debug
install is `com.blocktime.debug`, with the debug keystore's SHA-1. Register that pair too, or
debug builds fail sign-in while release builds work.

## Signing

The release key was generated for the owner and delivered to them directly; it is **not** in
this repo (`*.jks` and `keystore.properties` are git-ignored). Its fingerprint is the SHA-1
above, alias `blocktime`. The password was given in the chat session that created it — ask the
owner for it, don't guess and don't commit it anywhere.

To build a signed release locally, create `keystore.properties` in the repo root:

```properties
storeFile=/absolute/path/to/blocktime-release.jks
storePassword=...
keyAlias=blocktime
keyPassword=...
```

`app/build.gradle.kts` picks it up if present and silently produces an unsigned APK if not.
`tools/signing-key.sh` generates a fresh key and prints the SHA-1 and a base64 blob for CI.

## Outstanding work

1. **Finish the Google Cloud registration** (owner). Until then the app installs but Connect
   fails. No rebuild is needed afterwards.
2. **Add CI signing secrets**: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
   `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. The workflow then signs each build and attaches
   the APK to a rolling `latest` release. **Once that works, delete `dist/`** — a checked-in
   binary is a stopgap, not the intended distribution path.
3. Consider opening a PR to `main`, or deciding `main` keeps the old site permanently.

## Known limitations (deliberate, not bugs)

- Editing an occurrence of a repeating event changes that occurrence only; there is no
  "edit the whole series".
- Free/busy comes from the user's own selected calendars. The app does not check whether
  partners are free — that needs Google Calendar sharing, which the app does surface: a
  calendar shared with "Make changes to events" appears as writable and can receive blocks.
- No offline mode; without a connection the day view shows an error.
- Time zone follows the device.

## Plausible next features, in rough order of value

1. Week view (the day view's item model would extend to it; `Scheduling` already works over
   arbitrary windows).
2. "Edit whole series" for recurring blocks — needs the master event id, which the DTO layer
   currently drops apart from `recurringEventId`.
3. Offline cache of the current and next day, so the app opens to content rather than a spinner.
4. Home-screen widget or quick-settings tile for "block the next hour".

## Gotchas hit while building this

- GitHub runners generate a **random** debug keystore per run, so a CI-built debug APK changes
  fingerprint every build and breaks Google sign-in. That is why CI builds a *release* APK
  signed from secrets. Don't "simplify" it back to `assembleDebug`.
- Actions artifacts download as ZIPs, which a phone cannot install from. The workflow attaches
  the APK to a release for a one-tap install; keep that if the owner stays phone-only.
- Maven Central occasionally returns 429 through proxies during the first dependency
  resolution. It is transient — retry the build.
