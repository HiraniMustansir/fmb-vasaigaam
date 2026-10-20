# FMB Vasaigaam 0.4.0 — signed release candidate

20 October 2026. Direct APK distribution; Android 8.0 and newer. This is a signed candidate for committee acceptance, not a claim that the entire original brief is complete.

## Downloads

- [Signed release APK](../artifacts/FMB-Vasaigaam-0.4.0-release.apk): dedicated release certificate, not debuggable.
- [Debug update APK](../artifacts/FMB-Vasaigaam-0.4.0-debug.apk): updates the existing evaluation installation with the same debug certificate.
- [SHA-256 checksums](../artifacts/SHA256SUMS).

Do not uninstall an existing debug app merely to try release. Review/post its local drafts first and follow [signing and migration instructions](release-signing.md). Posted server records persist independently of the APK.

## Changes

- Emerald, ivory and gold light/dark colour schemes; corrected dark system bars, adaptive logo icon, bounded wide-screen content, keyboard-safe forms, wrapped quick actions and service status cards.
- Dashboard counts and shortcuts for today's plans, open tasks and local drafts. Server update times now use India time, with a separate saved-view label.
- Pages reset to the top when switching sections. Monday–Sunday menu navigation, previous/next week, This week, All plans and date headings.
- Accounts cash/bank balances and date-range reports for contributions, operating expenses, consumed ingredients and net cash/bank changes. Period boundaries use Asia/Kolkata posting dates and include reversals in their posting period.
- All-time service ingredient costs and cost per prepared tiffin/thaal, including additional cooking runs. No overhead allocation or profit estimate is implied.
- CSV user text is escaped and protected against spreadsheet formula interpretation.
- Synchronous admission guards prevent rapid repeated operations. Form post controls disable while busy; failed requests retain the same draft/idempotency key for retry. Sign-out confirmation explains device-local draft/cache deletion. My profile now prefills existing member values.
- Separate QA application ID protects the installed committee app and local data during device tests. A USB debug script avoids manual APK downloads.
- Dedicated private release key and repeatable signing/verification script; no private key/password in Git.

## Evidence

Builds: debug, release, QA and instrumentation APKs. Both debug and release lint pass with no errors; dependency/icon warnings remain. **32 unit tests, 11 physical-device UI tests and 57 isolated PostgreSQL checks pass.** Signature v2/v3 and 16 KB ZIP alignment pass. APK metadata is package `org.vasaigaam.fmb`, versionCode 4, versionName 0.4.0, minSdk 26 and targetSdk 36; release has no debuggable flag.

The connected Android API 37 phone received an in-place debug update. Its existing session survived and loaded the live workspace; Home, weekly menu, stock, accounts and reports rendered. No real records were created, no passwords were read/entered, and no account was registered by the agent. The workspace currently needs ingredients, suppliers, recipes, opening stock/balances and kitchen defaults entered by the committee. Empty-state rendering is not proof of a completed operational transaction.

Supabase public Auth settings now report email/password enabled, signups allowed and email confirmation OFF. No hosted dashboard/database changes were made in this release. A fresh registration, two-member sync, receipts and a complete live purchase → receipt → cook → payment scenario remain to be accepted. The first two are not proved by an existing session loading successfully.

## Remaining scope

See [validation](validation.md) for the complete list. Important missing features include purchase orders/confirmed incoming supply, invoice tax/discount/cancellation, consumption reversal, full-week copying, portion profile/conversion editors, recommendation/forecasting, notification inbox/push, localization and large-history pagination. Community verification of calculated Hijri dates is separate from the offline calendar provider.

Use [operator guide](operator-guide.md) for the supported workflows and [phone development](device-development.md) for fast updates.
