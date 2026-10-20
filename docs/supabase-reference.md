# Supabase technical reference for FMB Vasaigaam

For the dashboard-first setup process, start with [superbase.md](../superbase.md). The current app uses password registration with no email delivery or approval. Migration 003 must be deployed to the hosted project; no deployment of it is claimed. Its publishable key allows client requests, not schema administration.

## 1. Prerequisites

Use a Supabase account with project-owner access, current Supabase CLI, Docker for local tests, JDK 17 and Android SDK 36. Use a separate development project. Read the committed [validation report](validation.md) before handling live stock or accounts.

## 2. Create the project

Create a Supabase project in a suitable region, save the database password securely, and wait for it to become healthy. For the owner's existing project, the ref is `bdeqckqvvbllagezkugv`. Do not reset that project's database or replace existing schemas. Review migrations in a development project first.

## 3. Project URL and publishable key

In Supabase project Settings → API Keys / Data API, copy the project URL and client-safe publishable key (legacy anon key also supported by the SDK). Never use a secret/service-role key as the Android client key. The owner's supplied URL and publishable key are default build settings; they confer no membership.

## 4. Android configuration

Set `sdk.dir` in ignored `local.properties`. Optional `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` properties or environment variables override defaults. Rebuild the APK after changing them. Do not prefix native properties with NEXT_PUBLIC_; those names are a web convention.

## 5. Password authentication setup

Enable Email, enable new account signups and disable Confirm email under Authentication → Sign In / Providers. The app uses Supabase Kotlin `signUpWith(Email)` and `signInWith(Email)` with passwords. It checks that confirmation is disabled before attempting registration. No signup/sign-in email is intentionally requested. Supabase auto-confirms the account timestamp in this configuration; it is not evidence of inbox ownership. Session checks and membership validation still run server-side. See [configuration](https://supabase.com/docs/guides/auth/general-configuration) and [password authentication](https://supabase.com/docs/guides/auth/passwords).

## 6. Redirects and deep links

No redirect, magic-link callback or deep link is needed for this email/password flow. Keep the site's URL configuration appropriate for any other Auth flows you use. OAuth is not implemented.

## 7. Email delivery and password recovery

SMTP and email templates are not prerequisites for the current app. Do not configure dummy SMTP credentials. Email-based forgotten-password recovery is unavailable without email delivery. A trusted project administrator must establish identity outside the app and set the existing user's password via the server-side Supabase Admin API or a trusted operator tool. Never put admin credentials on the phone. If email recovery is introduced later, configure and test custom SMTP, sender verification, templates and Auth/provider quotas. [SMTP documentation](https://supabase.com/docs/guides/auth/auth-smtp).

## 8. Ordered migration deployment

Authenticate the CLI as the project operator. These commands require credentials beyond the publishable key:

```sh
supabase init
supabase login
supabase link --project-ref bdeqckqvvbllagezkugv
supabase db push --dry-run
supabase db push
```

Supply the database password securely when prompted. Review dry-run output first. Alternatively use the SQL editor to execute each migration file in the exact order below. Migrations are intended to run once, tracked by the CLI; do not rerun partially executed files without diagnosing the failure.

## 9. Exact schema files

1. [`supabase/migrations/202610200001_core.sql`](../supabase/migrations/202610200001_core.sql): tables, composite keys, RLS, Storage policies, indexes, balanced-journal trigger and demand view.
2. [`supabase/migrations/202610200002_commands.sql`](../supabase/migrations/202610200002_commands.sql): stock/journal helpers, FEFO allocation and original operational commands.
3. [`supabase/migrations/202610200003_password_registration.sql`](../supabase/migrations/202610200003_password_registration.sql): no-code profile registration, singleton workspace initialization, retired bootstrap/invitation entry points and the updated snapshot.

These files are the schema source of truth. **Never deploy `supabase/tests/local_platform.sql`** to Supabase: it only emulates Auth/Storage contracts for isolated PostgreSQL tests.

## 10. RLS and database permissions

All workspace tables have RLS. API roles receive SELECT with active-membership policies. Direct operational writes are revoked; writes go through the command function. Cross-workspace relationships use composite foreign keys. Owner-only operations are checked separately from position labels. SECURITY DEFINER functions have empty search_path and explicit grants. Private helpers cannot be invoked by API users. New tables/functions must follow the same rules. Review [Supabase RLS guidance](https://supabase.com/docs/guides/database/postgres/row-level-security).

## 11. Initial workspace creation

The current app requires no bootstrap token or designated email. The first authenticated account to finish its name/position setup creates the workspace and receives its owner_id. An advisory transaction lock prevents two simultaneous registrations from creating two workspaces or duplicate accounts. Subsequent users join the same workspace automatically. Existing workspace and operational records are preserved when applying migration 003. No server-side approval gate exists. The legacy bootstrap table remains for migration compatibility; unused authorizations expire and the public bootstrap command is disabled.

## 12. Membership and revocation

Anyone who creates a password account may complete their name/position and join the shared workspace. No invitation or owner approval is required. Descriptive position labels do not grant administration privileges; all active members have equal operational rights. The first completed profile owns workspace/member settings. Owner transfer and revocation remain separate operations.

A revoked account cannot regain access by repeating onboarding. Server RPCs, RLS and Storage checks reject future access, and cached data clears on the next successful membership check. As with any open registration model, a person could create another account with another email and register; there is no identity or invitation enforcement. A privately distributed APK is not an access-control boundary.

## 13. Private attachments

Migration 1 creates `committee-attachments`, private, limited to JPEG/PNG/PDF and 10 MB. Object paths start with the workspace UUID. SELECT and INSERT policies call current membership. No client update/delete policy exists, preserving evidence. Accounts → Receipts uploads evidence and registers its reference only after the object exists. Upload is not a posting workflow.

Opening evidence issues a signed URL valid for 60 seconds after a permission check. Such a link can survive revocation until expiry. Do not create public bucket URLs. A failed metadata registration can leave a private orphan object; operators can reconcile these against the attachments table. Attachment upload/download has not been tested against the hosted Storage service.

## 14. Realtime

Migration 1 adds `audit_events` to `supabase_realtime`. Enable Realtime for the project and confirm publication membership in the dashboard. The app subscribes to permitted audit inserts and refetches current records. Table RLS controls accessible changes. Foreground polling every 30 seconds remains as a fallback. Realtime delivery itself is not a transaction receipt and does not authorize writes.

## 15. Edge Functions

None are needed or included. There are no Edge Function deployment commands for this version. If automated invitation email or push is added later, use a separate authenticated server function that rechecks ownership/membership and keeps provider credentials server-side.

## 16. Secrets

Keep CLI tokens, DB passwords, bootstrap tokens, SMTP credentials and signing keystores outside Git. The APK includes only the client-safe publishable key. Bootstrap authorization is never included in a build. Android backup/device-transfer exclusions protect session/cache files from automatic backup. This is not a substitute for device access controls or a security review.

## 17. Background jobs and notifications

Android WorkManager checks membership and refreshes cached reads when connected, approximately every 15 minutes subject to OS scheduling. It never posts drafts automatically. There are no Supabase cron jobs, push credentials or push delivery setup. Tasks and activity are visible inside the app; push notifications and configurable notification preferences are not implemented.

## 18. Bohri calendar setup

The reference [Mumineen Calendar](https://www.mumineencalendar.com/) was inspected; no licensed API or verified dataset was established. No scraping, redistributed observances or generic Hijri conversion is included. The owner maintains `calendar_mappings` using Miqaats → Calendar with a source, verification status and Gregorian date. Editing uses a version check and appends audit history. No dates are verified by default. The available verified dates are exactly the individually flagged mappings; gaps remain unverified. This version lists those mappings rather than presenting a guaranteed continuous date range or calendar month grid.

Workspace defaults and calculations use Asia/Kolkata civil dates at midnight. There is no sunset rollover. Confirm community conventions before adding any conversion provider. Local observance-only entries and gatherings remain separate; observances never automatically generate a meal.

## 19. Development seed

In a separate development project, create a password account `fmb-owner@example.test` with confirmation disabled through your local Auth tooling (use an email deliverable in your dev environment if necessary and edit the seed accordingly). `supabase/seed.sql` refuses to run unless `fmb.allow_demo_seed=yes` is explicitly set. Use only on an empty development workspace:

```sh
PGOPTIONS='-c fmb.allow_demo_seed=yes' psql "$DEV_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/seed.sql
```

The seed creates an explicitly named DEMO workspace, a recipe, supplier, stock and 23-thaal plan. It is never loaded by the APK and never substitutes production data. The SQL test suite has its own transactional fixtures, rolled back after checks.

## 20. Permission and business tests

Run `python3 scripts/test_backend.py`. It starts an isolated PostgreSQL 16 container, installs the platform fixture, applies actual migrations, runs business tests, exercises separate concurrent sessions, checks API-role RLS/Storage policies, then destroys the container. Docker is required. These are not live Auth, email, Storage HTTP or Realtime integration tests. Review [validation](validation.md) for unverified scenarios.

Before rollout, test with two real users and a separate workspace: password registration/profile setup, shared edits, revoked access including Storage, duplicate receipts/cooking, partial payments, expired sessions and reconnects. An ordinary user must never be able to call private helpers or directly mutate ledger tables.

## 21. Backup, export and restore

Verify backup retention and point-in-time recovery availability for the selected Supabase plan; no plan capability is assumed. Use the Supabase dashboard and official [backup guidance](https://supabase.com/docs/guides/platform/backups). Maintain operator-controlled encrypted logical exports as needed, for example `supabase db dump --linked -f backup.sql` with the appropriate CLI schema/data flags after checking that CLI version's help. Test restoration in an isolated project before relying on a backup.

Database dumps do not constitute a complete backup of Storage object bytes, SMTP configuration or signing keys. Back up private attachment objects separately through an authorized operator process. Track hashes/paths and test retrieval after restore. Do not commit backup data. The app's journal CSV is a report, not a full database restore format. Preserve audit records and reconcile opening totals after a restoration.

## 22. Troubleshooting

- **Registration unavailable:** disable Confirm email, enable signups/Email and save. The app refuses to intentionally send confirmation emails.
- **No profile setup after sign-in:** deploy migration 003 and tap Refresh.
- **Account already exists:** use Sign in. A former OTP account may need an administrator to set its password; do not delete linked Auth identities as a shortcut.
- **Missing function/table:** install migrations 001, 002 and 003 in order. Do not re-run already installed migrations blindly.
- **Conflict:** refetch and deliberately review the latest record version.
- **Stock or payment rejected:** review quantities, reservations and outstanding balances.
- **RLS or private Storage denied:** check active membership and correct workspace paths; never put service-role keys in Android.
- **Realtime missing:** check publication membership and RLS; polling remains available.

## 23. Known limits and external dependencies

This initial app does not fulfill the entire attached product specification. Purchase orders/expected incoming supply, tax/discount/charges, invoice cancellation, full stock/production reversal chains, per-recipe portion profiles, demand overrides, rich reporting, continuous month calendar, calendar import, historical recommendations, OCR, localization extraction and comprehensive device accessibility tests remain work. See the precise feature/test matrix in [validation](validation.md).

Migration 003 deployment, Confirm email OFF, authoritative calendar data/permission, production signing and complete multi-member device acceptance require operator configuration. No successful hosted password registration or production readiness is claimed.
