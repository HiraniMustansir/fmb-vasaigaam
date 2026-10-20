# Develop on your phone

You do not need to download APKs from GitHub after every edit. Connect the Android phone by USB, enable Developer options → USB debugging and accept the computer's debugging prompt.

In Android Studio, select the phone from the device selector and click Run. For supported code changes while the app is running, use Apply Changes. Changes to resources, the manifest or dependencies may require Run again; Android Studio transfers the rebuilt app directly. Native Kotlin does not provide universal hot reload for every kind of change.

From this checkout, you can also run:

```sh
./scripts/debug_phone.sh
```

This builds, updates the debug app in place and launches it. It preserves the installed app's local data when the package and signing key match. It does not uninstall anything. The script chooses one USB device; set `ADB` if your SDK's adb is not on PATH. Use JDK 17 (`JAVA_HOME`) and configure `sdk.dir` in `local.properties`.

Device UI tests use the separate `org.vasaigaam.fmb.qa` package, never your main app's account, database or local drafts:

```sh
./gradlew :app:connectedQaAndroidTest
```

The QA package may show a second app icon during testing. Do not enter committee credentials in it. The final signed release is a separate distribution artifact; see [release signing](release-signing.md) before switching from debug.
