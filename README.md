# fmb-vasaigaam

Native Kotlin Android committee app for the Vasai Gaam FMB kitchen. Ivory, emerald and gold Material 3 UI uses the supplied Anjuman E Badri logo.

**Status: signed release candidate (0.4.0).** Core workflows build and pass local checks, and the existing signed-in phone loads its hosted workspace. Full hosted multi-member acceptance and several broader brief requirements remain outstanding. See [validation](docs/validation.md) for scope and gaps.

## Download the latest signed APK

**[Download FMB Vasaigaam v0.4.0 — Signed APK](https://github.com/HiraniMustansir/fmb-vasaigaam/raw/refs/tags/v0.4.0-rc1/artifacts/FMB-Vasaigaam-0.4.0-release.apk)**

Version **0.4.0** · Signed release candidate · Android **8.0+** (API 26). This is a directly installable release candidate, not an unsigned APK. It requires the backend setup in [superbase.md](superbase.md).

For existing evaluation installations, the [updated debug APK](artifacts/FMB-Vasaigaam-0.4.0-debug.apk) preserves the debug signing identity. The connected phone was updated with this build in place. **The release APK cannot replace a debug installation directly because their signing keys differ.** See [migration and private key backup instructions](docs/release-signing.md) before switching. Later signed releases can update each other in place.

Checksums: [SHA256SUMS](artifacts/SHA256SUMS). Private signing files are stored outside Git. No administrator credential is embedded; the supplied publishable key is a public client identifier.

New in 0.4.0: cohesive light/dark themes, adaptive launcher icon, wrapped quick actions, dashboard shortcuts, weekly date navigation, service status cards, local-time freshness, financial date-range summaries, per-service ingredient costs, CSV formula protection, duplicate-tap protection and explicit sign-out draft-loss confirmation.

[Develop directly on your phone](docs/device-development.md) without manually downloading every build. [Release scope and remaining work](docs/release-0.4.0.md).

## Implemented connected workflows

- Email/password registration and sign-in without SMTP; name/position onboarding automatically joins the shared workspace; member revocation and ownership transfer. Position labels confer no extra privileges.
- Home attention list and setup checklist; weekly menu navigation and chronological Miqaats; recipes with base yield, ingredient lines, allergens and preserved versions; service copying and Android sharing.
- Demand scaling, thaal counts, buffers, combined shortages, reservations, actual cooking, partial production and deliberate additional production postings. Observances create no food demand.
- Stock ledger, openings, partial invoice receipts, supplier returns, wastage, reconciliation, reversals and FEFO physical lot allocation. Negative available stock is blocked.
- Supplier invoices, partial payments, cash/bank, expenses, contributions, advances, settlements and reimbursements. Balanced double-entry journals; reversals retain history. CSV sharing and date-range accounts summaries; per-service ingredient cost and cost per prepared tiffin/thaal.
- Native offline Bohri/Misri month calendar based on dawoodibohracalendar.in, Gregorian dates, source links, local Miqaats and verified manual corrections; tasks, notes, activity; private receipt uploads and short-lived attachment access.
- Room drafts and read cache, visible freshness, Realtime invalidation plus 30-second foreground polling, and WorkManager membership checks. Drafts never post automatically.

## Build and tests

Requires JDK 17, Android SDK 36 / Build Tools 35.0.0 and network access to Google Maven/Maven Central. Gradle wrapper is committed.

```sh
# Set sdk.dir=/absolute/path/to/Android/Sdk in ignored local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:lintRelease
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

Calendar method, source attribution, calculated range and verification limits are documented in [docs/calendar.md](docs/calendar.md).
