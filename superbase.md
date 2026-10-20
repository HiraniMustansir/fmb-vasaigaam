# Supabase setup — email and password, no SMTP

The current app (0.2.0) uses this flow:

**Create account → enter your name and committee position → Finish setup → Home.**

No email code, invitation, owner approval, or bootstrap authorization is needed. Existing members sign in with their password. Anyone who registers can join the one shared committee workspace. An email address is an account identifier; the app does not prove ownership of its inbox.

## What I checked

On 20 October 2026, the project at `https://bdeqckqvvbllagezkugv.supabase.co` was reachable. Email sign-in and Auth account creation were enabled. The `workspace_snapshot` RPC was present and correctly refused an anonymous call. **Email confirmation was still enabled** (`mailer_autoconfirm=false`), so change that setting in Step 2.

I have not changed dashboard settings or deployed the new registration migration to your hosted project. The publishable key is client-safe configuration, not an administrator credential.

## Step 1 — Install the registration migration

Open [your existing Supabase project](https://supabase.com/dashboard/project/bdeqckqvvbllagezkugv), then **SQL Editor → New query**.

If you already successfully ran migrations 001 and 002, run **only** the new file:

[`supabase/migrations/202610200003_password_registration.sql`](supabase/migrations/202610200003_password_registration.sql)

1. Open that file in your IDE and copy its complete contents.
2. In a new SQL Editor query, put `BEGIN;` on the first line.
3. Paste the entire file underneath.
4. Put `COMMIT;` on the last line.
5. Run the whole query. Expect a success message.
6. To name it, start typing/pasting, find it under PRIVATE, right-click its name → Rename query → `FMB 03 - Simple registration`.

The migration preserves existing workspace, membership, stock and financial data. It adds automatic profile setup, keeps the original operational transaction function private, and retires the old bootstrap/invitation entry points. It does not disable RLS.

For a completely new database, run all files in order:

1. [202610200001_core.sql](supabase/migrations/202610200001_core.sql)
2. [202610200002_commands.sql](supabase/migrations/202610200002_commands.sql)
3. [202610200003_password_registration.sql](supabase/migrations/202610200003_password_registration.sql)

Run each inside its own BEGIN/COMMIT transaction. Stop on any error rather than deleting tables or re-running already installed files. Do not execute `supabase/tests/*` or `supabase/seed.sql` on your actual committee project.

SQL Editor execution does not record Supabase CLI migration history. Reconcile that history before switching to CLI deployment later.

## Step 2 — Turn off email confirmation

In Supabase:

1. Open **Authentication → Sign In / Providers**.
2. Find and open **Email** under Auth Providers. Some dashboard versions display the confirmation toggle in the page's general settings instead.
3. Keep the Email provider enabled.
4. Set **Confirm email** to **OFF**.
5. Keep **Allow new users to sign up** enabled.
6. Click **Save changes**.

Deploy migration 003 before turning confirmation off, so the app uses the new membership flow. You do not need SMTP, Gmail App passwords, email templates, a sending domain, or magic-link redirects for registration/password sign-in. Leave any incomplete SMTP form unsaved.

The app checks the public Auth configuration before creating an account. If confirmation is still enabled, it stops before requesting signup rather than intentionally triggering an email.

Supabase implicitly marks password accounts confirmed when this setting is off; that timestamp is not evidence that the user verified an inbox. [Official configuration guidance](https://supabase.com/docs/guides/auth/general-configuration).

## Step 3 — Use the updated app

The old 0.1.0 build has an email-code screen, so use 0.2.0 or a direct development deployment from the current source.

1. Open FMB Vasaigaam.
2. Tap **New here? Create an account**.
3. Enter your email, choose a password of at least 8 characters, and repeat the password.
4. Tap **Create account**. There is no email-code step.
5. Enter your name and committee position, such as Secretary, Treasurer, Joint Treasurer, Kitchen Coordinator, Purchase Coordinator or Committee Member. Custom positions are allowed.
6. Tap **Finish setup**. You should reach Home.

The first person to finish setup creates the workspace and becomes its owner for settings/member administration. Later members join that same workspace automatically. Their chosen position is a descriptive label; every active member has operational editing rights. Concurrent setup attempts cannot create duplicate workspaces or default accounts.

Existing members choose **Sign in** with their email and password. The app retains Supabase sessions; passwords are not saved in Room, app drafts, reports or Git.

## Step 4 — Verify setup

Use these read-only SQL queries:

```sql
select name, timezone, owner_id from public.workspaces;

select name, position, active from public.memberships;

select to_regprocedure('public.execute_command(text,jsonb,uuid)');
```

After the first setup, expect one FMB workspace, an active member and a non-NULL execute_command signature. After the next member finishes setup, expect another member in the same workspace.

To verify that migration 003's registration flow is installed:

```sql
select position('onboarding' in pg_get_functiondef(
  'public.execute_command(text,jsonb,uuid)'::regprocedure
)) > 0 as simple_registration_installed;
```

Expect `true`.

## Step 5 — Configure your kitchen

1. Home → Members → Kitchen settings: enter defaults.
2. Stock: add ingredients and their base units.
3. Stock → Suppliers: add suppliers.
4. Menu → Recipes: add recipes and ingredient quantities.
5. Stock → Record movement → Opening: record physical opening stock and costs.
6. Accounts → Record money → Opening: record opening cash/bank balances.

There is no Invite member action. Core members use Create account and finish their profile themselves.

## Password recovery and existing email-code accounts

Email-based password reset is not available without configured email delivery. Contact the project administrator for a trusted administrative password reset, after they establish your identity outside the app. No administrator/service-role key is stored on the phone.

An account previously created through email OTP may have no password. Registering the same address again does not automatically set a password. The project administrator must set a password for that existing Auth user through the Supabase Admin API or another trusted operator tool. Do not delete an Auth user with existing committee records just to recreate it; that can break linked history.

## Troubleshooting

| Problem | What to check |
|---|---|
| Create account says registration is unavailable | Confirm email must be OFF, signups ON, Email provider ON. Save the settings. |
| Old email-code screen | You are running 0.1.0; deploy the current app build. |
| Signed in but profile setup is unavailable | Run migration 003, then tap Refresh. |
| Missing function/table | Install missing migrations in order. |
| Object already exists during migration | Stop and share the exact error; do not reset the project. |
| Wrong email/password | Use Sign in for an existing account. Check with the project administrator if it was created with OTP and has no password. |
| Password rejected | Use at least 8 characters and satisfy any stronger password rules configured in Supabase. |
| Revoked access | The administrator must review it; repeating profile setup cannot reactivate a revoked account. |

After confirming that SQL objects exist, you can refresh a stale Data API cache with:

```sql
notify pgrst, 'reload schema';
```

## Access model and technical reference

The app still requires a password session and membership before operational data is accessible. Server membership checks, private Storage, audit history and transactional stock/accounting rules remain. Membership is automatically granted when a registered user completes their profile; distributing the APK privately is not a server-side approval boundary.

The default workflow assumes a single committee workspace. If multiple workspaces exist in the project, the administrator must configure the intended workspace rather than allowing automatic selection.

See [docs/supabase-reference.md](docs/supabase-reference.md) for migrations, backend security, Storage, Realtime, tests, development seeds, backup/restore and known limits. See [docs/validation.md](docs/validation.md) for what was actually tested.
