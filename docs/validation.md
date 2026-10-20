# Validation report — 20 October 2026

**Initial implementation, not production-ready.** This report distinguishes local test evidence from hosted and device acceptance. The original brief is broader than the implemented workflows.

## Passed locally

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`: builds an installable debug-signed APK; 25 JUnit tests pass (seven quantity/domain, seven password-flow and eleven calendar tests); lint has no errors. Dependency update and launcher icon warnings remain. Versions are pinned as a compatible set; they are not claimed to be the newest versions.
- `python3 scripts/test_backend.py`: **57 passed assertions/checks**, with output in [backend-test-results.txt](backend-test-results.txt). It applies all three migrations to a fresh PostgreSQL 16 container. Business assertions verify automatic profile onboarding without codes or approval, thaal scaling, single buffer application, observance exclusion, duplicate requests, idempotency payload mismatch, stock shortages, invoice/receipt separation, partial receipts, over-receipt rejection, supplier return/reversal, one-time reversal, partial payments, reservations/release, stale versions, production retry/completion checks, negative stock, balanced journals, invoice/payment distinction, registration/profile access boundaries, member financial edits, label-based privilege escalation prevention, and revoked writes/reads.
- Separate concurrent database connections verify two concurrent registrations join exactly one workspace with exactly one set of default accounts, exactly one successful stock issue when two would overspend, and exactly-once posting under two simultaneous identical requests.
- Queries under the `authenticated` API role verify workspace read isolation, private attachment read isolation, permitted own-workspace upload/denied foreign-workspace upload, and denial of direct stock-ledger writes.
- BigDecimal domain tests cover kg/g conversion, rejecting mass/volume guesses, 23 × 8 = 184, upward thaal rounding, buffer scaling, shared demand shortage, nonnegative shortages and invalid yield.

The backend test fixture emulates `auth.uid()`, Auth users, Storage tables and a Realtime publication. PostgreSQL warns that its local wal_level does not support logical delivery; publication creation is tested, Realtime transport is not. Test data is isolated and discarded. These checks do not validate the actual Supabase Auth/Storage HTTP services.

## Hosted checks and external setup

The project is reachable. Its snapshot RPC now exists and rejects an anonymous call with permission denied, as expected. A read-only Auth settings request still reported `mailer_autoconfirm=false` (Confirm email ON) and `disable_signup=false` during this change. No dashboard setting has been changed by the agent, and migration 003 has not been deployed to the hosted project by the agent. Apply it after the previously deployed migrations 001/002, then turn Confirm email OFF before creating a password account.

No SMTP, email template, email verification or invitation is needed for the requested registration flow. Any password account can finish its profile and join the shared workspace. Positions remain descriptive; first completed setup owns the workspace. Email identifiers are not inbox-verified. Password recovery requires an identity-checked project administrator when SMTP is absent. Former OTP accounts may need an administrative password set rather than being registered again.

No successful hosted password registration or end-to-end onboarding is claimed. The password tests use a fake gateway for validation, configuration refusal and session lifecycle cases; they do not exercise real Supabase signup. Hosted Storage, Realtime, expired-session recovery and multi-member device acceptance still need testing. No authoritative Bohri calendar dataset or redistribution permission was established.

## Device checks

The 0.2.0 debug APK was installed directly on the connected physical Android API 37 phone using ADB. The package launched successfully in the foreground and the email/password sign-in screen was observed. The user was interacting with the phone; no personal account credentials were entered by the agent. Screenshots containing user-entered account information are not committed. This confirms installation and login-screen rendering, not successful registration, profile completion, all screen layouts, large text, tablet adaptation or process-death restoration.

The earlier emulator attempts failed during startup; they do not count as app acceptance. The installable APK is debug-signed. An unsigned release APK also builds and requires operator signing. Migration tests include an upgrade from an existing 001/002 database with an existing owner and inventory item; migration 003 preserves those records and enrolls an additional member in the same workspace. API users are denied direct calls to the private legacy operational function.

## Calendar validation in 0.3.0

The complete build, unit tests, lint, release build and connected Android test task pass. Eleven new calendar unit tests cover the source's anchor and displayed Ramadan dates, every-day round trips over the chosen 1446–1500 AH range, leap days/year transitions, invalid/out-of-range dates, manual correction precedence, verification status, navigation and Asia/Kolkata midnight boundaries.

Seven Compose instrumentation tests passed on the connected physical Android API 37 phone: native Misri month and Gregorian reference dates, previous/next navigation, Gregorian mode, reviewed manual correction display, explicit unavailable status before the supported anchor, observance-only defaults when planning, and a source action that runs only when tapped. Tests use isolated UI data and never sign in or post live committee records. The final 0.3.0 app is installed separately after the device test runner's cleanup. Full hosted calendar synchronization and community verification are not claimed.

Six additional local PostgreSQL checks verify unverified source dates remain unverified, reviewed mappings update versions/provenance, stale updates are rejected, audit history is preserved, changing a position label cannot grant date-verification administration and all active members see the same reviewed correction.

The source website was inspected directly. Mathematical parameters are implemented independently; the site's JavaScript, event catalogue and artwork are not bundled. No published API or redistribution licence was established. The provider works offline; it does not poll the site for updates or assert that its computed range is community-verified. See [calendar.md](calendar.md).

## Password flow checks

Seven additional JUnit tests cover email normalization, preservation of password whitespace, password sign-in without registration, refusing signup before it can send an email when confirmation is enabled, rejecting registration without a returned session, mismatched passwords, invalid addresses, blank sign-in passwords and short new passwords. Passwords are not stored in saveable Compose state, Room drafts or snapshots. Server-side password policy/rate-limit enforcement remains Supabase Auth's responsibility.

## Acceptance scenarios

| Brief scenario | Evidence and current limit |
|---|---|
| New member registers and completes a profile | Replaced by open password registration/profile setup at the user’s request; database onboarding/owner checks pass, full hosted flow pending |
| Two members see/edit same menu | Backend membership/version mechanisms implemented; simultaneous device UI/Realtime run pending |
| Menu generates requirements and shortages | SQL scaling and domain tests pass; combined shopping view is implemented; full UI scenario pending |
| Purchase received once | Partial receipt and duplicate receipt tests pass |
| Cooking consumes once and stores output | Database duplicate/completion tests pass; actual device workflow pending |
| Plan 23-thaal event | 184-person demand and once-only buffer verified in SQL and Kotlin |
| Regular/gathering combined shopping | Shared demand aggregation implemented; Kotlin aggregation calculation covered; broad multi-dish UI scenario pending |
| Supplier credit and partial payment | Local ledger outstanding and balanced posting tests pass |
| Leftovers/waste in history/reports | Production stores outputs and shows history; no analytical reporting suite |
| Revoke member | Local server permission/RLS tests pass; real revoked-phone cache behavior pending |

## Implemented but needing additional tests

Additional local assertions cover FEFO expiry ordering, reconciliation and reversal, advance settlement limits, partial reimbursements, partial production, additional production after completion, remaining demand and preserved recipe snapshots. Calendar override versions, conflict rejection, audit history and owner-only verification now have dedicated backend tests. Receipt upload metadata and Room/WorkManager lifecycle paths are not comprehensively tested. Verify these before relying on them operationally.

## Incomplete brief requirements

- No purchase orders, manual shopping lines, confirmed incoming quantity management, invoice tax/discount/extra charges, invoice cancellation or invoice editing/reversal workflow. Only original invoice lines, receipts, returns and payments are supported. Shopping shortages do not deduct unreceived invoice quantities or purchase-order promises.
- No item aliases/conversion editor, purchasing pack UI or configurable tiffin/thaal portion profiles. Entry uses canonical ingredient units. `item_conversions` is schema preparation only. Recipe overrides, production consumption reversal and full amendment/correction chains are not complete.
- No per-service production demand override, recommendation engine, forecast from historical waste, per-event actual-cost summary, per-tiffin cost report, reconciliation report or date-range reporting/search/filter suite. Journal CSV and underlying consumption costs exist.
- Menu plans are a chronological service list; no weekly grid, full-week copying, holiday calendar or service suspension configuration. Copying an individual service, cancellation and multiple services per date are supported.
- Calendar now has an offline source-referenced Misri provider, native month grids and individual-date corrections. Community/religious verification, authorized automatic observance import and live external-source browser integration remain unverified. See [calendar.md](calendar.md).
- Basic tasks, notes and activity exist; no in-app notification inbox/preferences, push, OCR or reviewed import UI.
- Kitchen defaults and a record-derived setup checklist are present; a full staged/resumable onboarding wizard is not. One active workspace is selected automatically; multi-workspace selection is not provided.
- UI strings have not all been extracted for localization. Adaptive tablet layouts and comprehensive accessibility/device QA are pending. A single snapshot RPC loads all records, so large historical datasets need pagination.

Use a development project and evaluation data until critical remaining workflows have been tested and the relevant missing features are completed. See `docs/operator-guide.md` for supported procedures and `superbase.md` for deployment.
