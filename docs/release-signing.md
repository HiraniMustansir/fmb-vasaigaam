# Release signing and updates

Version 0.4.0 includes a **signed release candidate** for direct APK distribution. It uses a dedicated RSA 3072-bit key, not the Android debug key. Release builds are not debuggable. APK Signature Scheme v2 and v3 and 16 KB ZIP alignment were verified with Android SDK tools.

Public certificate SHA-256:

```text
671b7fcb55c10b21e8a0bff2ec164a2faf6463c236a675a632ba0d443955a627
```

## Back up the signing key

On the development computer, private files are stored **outside this repository**:

```text
/home/muss/.local/share/fmb-vasaigaam/signing/fmb-release.p12
/home/muss/.local/share/fmb-vasaigaam/signing/store-password.txt
```

The directory is mode 700 and files mode 600. Back up both securely to encrypted storage or a password manager. They are not uploaded to GitHub. Losing the key prevents normal updates to release installations; exposing it allows others to sign app updates. Never attach these files to an issue or send them in chat.

## Build another signed APK

Use JDK 17 and configure the Android SDK in ignored `local.properties` (or `ANDROID_SDK_ROOT`). Increment versionCode/versionName first, then:

```sh
./gradlew :app:assembleRelease :app:lintRelease
python3 scripts/sign_release.py --output artifacts/FMB-Vasaigaam-NEXT-release.apk
```

The signing script reuses the existing private key, verifies the signature and ZIP alignment, and prints only public certificate information/checksums. `FMB_SIGNING_DIR` may point to a restored private directory outside the repository. `--init` is for creating a key for the first time; do not replace an existing release key.

## Move from the evaluation build

The existing debug APK and signed release APK share `org.vasaigaam.fmb` but have different certificates. Android will not install the release over the debug build. Keep using the updated debug build during acceptance. When deliberately moving to release, post/review local drafts first; uninstalling debug erases device-local drafts/cache/session. Server-posted committee records remain in Supabase. Install the signed release and sign in with the same account.

Later release APKs signed with this same key and a higher versionCode update the release installation in place. Debug APKs continue updating existing debug installations. The release candidate has not replaced the debug installation on the connected phone.

See [Android app signing](https://developer.android.com/studio/publish/app-signing) and [validation](validation.md). Signing an APK does not establish completion of every product requirement or hosted acceptance test.
