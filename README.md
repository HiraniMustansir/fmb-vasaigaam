# fmb-vasaigaam

Native Kotlin Android committee app for the Vasai Gaam FMB kitchen. Ivory, emerald and gold Material 3 UI uses the supplied Anjuman E Badri logo.

**Status: initial implementation, not production-ready.** The APK builds and important database rules pass local tests. Hosted Supabase setup, end-to-end device workflows and the full acceptance brief are not yet verified. See [validation](docs/validation.md) for scope and gaps.

## APK

Install [artifacts/FMB-Vasaigaam-0.2.0-debug.apk](artifacts/FMB-Vasaigaam-0.2.0-debug.apk) on Android 8.0+ (API 26). This is an installable **debug-signed evaluation build**. It requires the backend setup in [superbase.md](superbase.md). The project URL and client-safe publishable key supplied by the owner are compiled into this build; they are not deployment credentials.

An [unsigned release APK](artifacts/FMB-Vasaigaam-0.2.0-release-unsigned.apk) is also included for the operator to sign; it cannot be installed until signed.

Check the APK against [SHA256SUMS](artifacts/SHA256SUMS). Release signing is described in [docs/release-signing.md](docs/release-signing.md). Never distribute a debug build as a production release.

## Implemented connected workflows

- Email/password registration and sign-in without SMTP; name/position onboarding automatically joins the shared workspace; member revocation and ownership transfer. Position labels confer no extra privileges.
- Home attention list and setup checklist; chronological menus and Miqaats; recipes with base yield, ingredient lines, allergens and preserved versions; service copying and Android sharing.
- Demand scaling, thaal counts, buffers, combined shortages, reservations, actual cooking, partial production and deliberate additional production postings. Observances create no food demand.
- Stock ledger, openings, partial invoice receipts, supplier returns, wastage, reconciliation, reversals and FEFO physical lot allocation. Negative available stock is blocked.
- Supplier invoices, partial payments, cash/bank, expenses, contributions, advances, settlements and reimbursements. Balanced double-entry journals; reversals retain history. CSV sharing.
- Verified manual Bohri date mappings with audit history; tasks, notes, activity; private receipt uploads and short-lived attachment access.
- Room drafts and read cache, visible freshness, Realtime invalidation plus 30-second foreground polling, and WorkManager membership checks. Drafts never post automatically.

## Build and tests

Requires JDK 17, Android SDK 36 / Build Tools 35.0.0 and network access to Google Maven/Maven Central. Gradle wrapper is committed.

```sh
# Set sdk.dir=/absolute/path/to/Android/Sdk in ignored local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 scripts/test_backend.py  # requires Docker; starts/removes an isolated PostgreSQL 16 container
```

Optional ignored `local.properties` overrides (environment variables with the same names also work):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_PUBLISHABLE_KEY=your-client-safe-publishable-key
```

No service-role key or signing credential belongs in the app or Git. The supplied publishable key is intentionally a public client identifier. Security depends on server membership checks, database grants and RLS.

## Project layout

- `app/`: Compose UI, ViewModel/Flow, Koin repository, Supabase SDK and Room/WorkManager.
- `app/src/main/java/org/vasaigaam/fmb/domain/`: decimal scaling and compatible unit conversions.
- `supabase/migrations/`: ordered schema, RLS/Storage rules and transactional commands.
- `supabase/tests/`: database business rules plus a minimal local Supabase platform fixture.
- `scripts/test_backend.py`: migration, RLS and actual concurrent-session tests.
- [superbase.md](superbase.md): deployment instructions, registration and security.
- [operator guide](docs/operator-guide.md), [architecture](docs/architecture.md), [validation](docs/validation.md).

Registration is open: anyone with the app/backend details can create an account and complete profile setup. No email verification or invitation is required. Password recovery needs a trusted project administrator when email delivery is not configured.

No bill, menu or contribution-distribution attachment was supplied. This version makes no assumptions about household contribution rates or per-thaal charges.
