# Release signing

The committed APK is debug-signed for evaluation. No production signing key has been created or committed.

Create and securely back up a dedicated signing keystore **outside this repository**, following Android's [app-signing guidance](https://developer.android.com/studio/publish/app-signing). Keep its passwords in a password manager. Use Google Play App Signing if distributing through Play.

Build an unsigned release:

```sh
./gradlew :app:assembleRelease
```

Then align and sign with SDK tools. Paths are examples; replace them with your own. Let apksigner prompt for passwords rather than putting them in shell history.

```sh
zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/fmb-aligned.apk
apksigner sign --ks /secure/path/fmb-release.jks --ks-key-alias fmb --out /tmp/FMB-Vasaigaam-release.apk /tmp/fmb-aligned.apk
apksigner verify --verbose --print-certs /tmp/FMB-Vasaigaam-release.apk
sha256sum /tmp/FMB-Vasaigaam-release.apk
```

Archive the signature certificate fingerprint and APK checksum with the release. Increment versionCode for each update. A debug build cannot be updated in place by a release signed with a different key; uninstalling it erases local drafts/cache. Verify all critical hosted and device workflows in `docs/validation.md` before a production release.
