# Supabase setup for FMB Vasaigaam

The exact filename `superbase.md` follows the requested deliverable. This version has **not** deployed migrations to the supplied hosted project. Its publishable key allows client requests, not schema administration.

## 1. Prerequisites

Use a Supabase account with project-owner access, current Supabase CLI, Docker for local tests, JDK 17 and Android SDK 36. Use a separate development project. Read the committed [validation report](docs/validation.md) before handling live stock or accounts.

## 2. Create the project

Create a Supabase project in a suitable region, save the database password securely, and wait for it to become healthy. For the owner's existing project, the ref is `bdeqckqvvbllagezkugv`. Do not reset that project's database or replace existing schemas. Review migrations in a development project first.

## 3. Project URL and publishable key

In Supabase project Settings → API Keys / Data API, copy the project URL and client-safe publishable key (legacy anon key also supported by the SDK). Never use a secret/service-role key as the Android client key. The owner's supplied URL and publishable key are default build settings; they confer no membership.

## 4. Android configuration

Set `sdk.dir` in ignored `local.properties`. Optional `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` properties or environment variables override defaults. Rebuild the APK after changing them. Do not prefix native properties with NEXT_PUBLIC_; those names are a web convention.

## 5. Email authentication

Enable the email provider and email confirmation. The app uses passwordless email OTP, followed by `verifyEmailOtp(EMAIL)`. Configure the Magic Link and Confirm Signup templates to display `{{ .Token }}` as a code. The app does not parse a magic-link callback. Verified email is enforced again inside database functions, and public Auth registration never creates workspace membership.

## 6. Redirects and deep links

This build uses manual code entry, so no redirect or Android deep link is needed. Set the Auth Site URL to an operator-controlled HTTPS page for fallback links. Do not add broad wildcard redirect patterns. OAuth and magic-link deep-link sign-in are not implemented; adding those later requires Android intent filters, app-link verification and an exact Auth redirect allowlist.

## 7. Production email delivery

Configure custom SMTP under Auth settings, verify the sending domain and test real code delivery. Review the current project's Auth rate limits, token length/expiry, resend interval and SMTP provider quotas. Default Supabase email delivery has development restrictions; do not assume it serves 10–20 real members. Check [Auth email documentation](https://supabase.com/docs/guides/auth/auth-email) and the dashboard for current limits. No fixed quota is assumed here.

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

1. [`supabase/migrations/202610200001_core.sql`](supabase/migrations/202610200001_core.sql): tables, composite keys, RLS, Storage policies, indexes, balanced-journal trigger and demand view.
2. [`supabase/migrations/202610200002_commands.sql`](supabase/migrations/202610200002_commands.sql): private stock/journal helpers, FEFO allocation, commands and workspace snapshot.

These files are the schema source of truth. **Never deploy `supabase/tests/local_platform.sql`** to Supabase: it only emulates Auth/Storage contracts for isolated PostgreSQL tests.

## 10. RLS and database permissions

All workspace tables have RLS. API roles receive SELECT with active-membership policies. Direct operational writes are revoked; writes go through the command function. Cross-workspace relationships use composite foreign keys. Owner-only operations are checked separately from position labels. SECURITY DEFINER functions have empty search_path and explicit grants. Private helpers cannot be invoked by API users. New tables/functions must follow the same rules. Review [Supabase RLS guidance](https://supabase.com/docs/guides/database/postgres/row-level-security).

## 11. One-time first-secretary bootstrap

Generate a high-entropy token outside Git, for example `openssl rand -hex 32`. Deliver it privately to the designated secretary. In the SQL editor, authorize that exact email and a short expiry. Replace placeholders before execution; never commit the token or its hash.

```sql
insert into public.bootstrap_authorizations(slot,email,token_hash,expires_at)
values (true, 'designated-secretary@example.com',
        encode(extensions.digest('REPLACE_WITH_RANDOM_TOKEN','sha256'),'hex'),
        now() + interval '1 hour');
```

The secretary signs in and verifies that same email, chooses First secretary setup, and enters name/token. The server locks the single authorization row, verifies email/token/expiry and ensures no workspace exists. It creates the owner membership, accounts and workspace atomically. Concurrent claims cannot create two owners. `claimed_at` disables the token automatically. An operator can disable an unused authorization immediately with:

```sql
update public.bootstrap_authorizations set expires_at=now() where slot;
```

Do not reset claimed_at or create replacement production workspaces as a shortcut. Recovery requires the project operator to inspect Auth identity and membership in the SQL editor.

## 12. Invitations and revocation

Home → Members → Invite member stores the approved email with a seven-day expiry. The app does not send invitation emails; the owner tells the approved person to install/sign in. Only a verified matching email can accept that invitation. The member chooses a descriptive position. Operational privileges are equal.

Revocation marks membership inactive and expires outstanding invitations to that email. Server RPCs, RLS and Storage checks reject future access immediately. Cached data is cleared on the device's next successful membership check; disconnected devices cannot receive revocation immediately. Ownership transfer must target an active member, and the owner cannot revoke themself before transferring.

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

In a separate development project, create and verify `fmb-owner@example.test` through your local Auth tooling (use an email deliverable in your dev environment if necessary and edit the seed accordingly). `supabase/seed.sql` refuses to run unless `fmb.allow_demo_seed=yes` is explicitly set. Use only on an empty development workspace:

```sh
PGOPTIONS='-c fmb.allow_demo_seed=yes' psql "$DEV_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/seed.sql
```

The seed creates an explicitly named DEMO workspace, a recipe, supplier, stock and 23-thaal plan. It is never loaded by the APK and never substitutes production data. The SQL test suite has its own transactional fixtures, rolled back after checks.

## 20. Permission and business tests

Run `python3 scripts/test_backend.py`. It starts an isolated PostgreSQL 16 container, installs the platform fixture, applies actual migrations, runs business tests, exercises separate concurrent sessions, checks API-role RLS/Storage policies, then destroys the container. Docker is required. These are not live Auth, email, Storage HTTP or Realtime integration tests. Review [validation](docs/validation.md) for unverified scenarios.

Before rollout, test with two real users and a separate workspace: invitation matching, shared edits, revoked access including Storage, duplicate receipts/cooking, partial payments, expired sessions and reconnects. An ordinary user must never be able to call private helpers or directly mutate ledger tables.

## 21. Backup, export and restore

Verify backup retention and point-in-time recovery availability for the selected Supabase plan; no plan capability is assumed. Use the Supabase dashboard and official [backup guidance](https://supabase.com/docs/guides/platform/backups). Maintain operator-controlled encrypted logical exports as needed, for example `supabase db dump --linked -f backup.sql` with the appropriate CLI schema/data flags after checking that CLI version's help. Test restoration in an isolated project before relying on a backup.

Database dumps do not constitute a complete backup of Storage object bytes, SMTP configuration or signing keys. Back up private attachment objects separately through an authorized operator process. Track hashes/paths and test retrieval after restore. Do not commit backup data. The app's journal CSV is a report, not a full database restore format. Preserve audit records and reconcile opening totals after a restoration.

## 22. Troubleshooting

- **PGRST202 / missing workspace_snapshot:** apply both migrations and allow the Data API schema cache to refresh. The supplied project returned this error during validation.
- **No code / only a link:** configure SMTP and templates to show Token; check delivery logs, expiry and rate limits.
- **Verified user sees no workspace:** approve their exact email and have them accept an unexpired invitation. Signing up alone grants no access.
- **Bootstrap rejected:** check designated email, verification, token hash, expiry and claimed_at. Never expose the row to client roles.
- **Conflict:** refetch and review current state. Do not override record versions silently. Release reservations before editing menus/recipes they depend on.
- **Receipt rejected:** match the ingredient and invoice line; use a positive receipt quantity within remaining delivery. Returns must be negative and within received quantity.
- **Stock rejected:** review on-hand/reservations and selected batches. Reconcile only after physical counting and release affected reservations deliberately.
- **RLS / Storage denied:** check current membership, UUID path prefix, file type/size and bucket policies. Never solve this by using service-role credentials on the phone.
- **No live updates:** check publication/RLS and network; tap Refresh. Polling also refreshes the app.
- **Posted draft still shown after reconnect:** retry the same saved draft/key, then refresh. The server returns its original result without posting again.

## 23. Known limits and external dependencies

This initial app does not fulfill the entire attached product specification. Purchase orders/expected incoming supply, tax/discount/charges, invoice cancellation, full stock/production reversal chains, per-recipe portion profiles, demand overrides, rich reporting, continuous month calendar, calendar import, historical recommendations, automatic invitations, OCR, localization extraction and comprehensive device accessibility tests remain work. See the precise feature/test matrix in [validation](docs/validation.md).

Real email, project migration deployment, authoritative calendar data/permission, production signing and two-member device acceptance require operator configuration. No successful hosted deployment, email authentication or production readiness is claimed.
