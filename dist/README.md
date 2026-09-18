# Prebuilt APK

`BlockTime.apk` is a signed release build, published here so it can be downloaded and
installed directly on a phone without a build machine.

- Signed with the project's release key; SHA-1 `E0:0F:26:E3:9A:8A:8E:C2:1E:19:D8:14:43:F0:E3:F7:81:CC:A6:72`
- Package `com.blocktime`, version 1.0.0

It contains no credentials. Google identifies the app by package name plus signing
fingerprint, so a copy of the APK cannot be used to access anyone's calendar.

Once the `RELEASE_*` secrets are configured (see [INSTALL.md](../INSTALL.md)), CI attaches a
fresh signed APK to the rolling `latest` release on every push, and this checked-in copy can
be deleted.
