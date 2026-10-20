# Validation report — 20 October 2026

**Initial implementation, not production-ready.** This report distinguishes local test evidence from hosted and device acceptance. The original brief is broader than the implemented workflows.

## Passed locally

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`: builds an installable debug-signed APK; seven JUnit domain tests pass; lint has no errors. Dependency update and launcher icon warnings remain. Versions are pinned as a compatible set; they are not claimed to be the newest versions.
- `python3 scripts/test_backend.py`: **47 passed assertions/checks**, with output in [backend-test-results.txt](backend-test-results.txt). It applies both migrations to a fresh PostgreSQL 16 container. Business assertions verify token-protected/one-time bootstrap, thaal scaling, single buffer application, observance exclusion, duplicate requests, idempotency payload mismatch, stock shortages, invoice/receipt separation, partial receipts, over-receipt rejection, supplier return/reversal, one-time reversal, partial payments, reservations/release, stale versions, production retry/completion checks, negative stock, balanced journals, invoice/payment distinction, invitation email matching, member financial edits, label-based privilege escalation prevention, and revoked writes/reads.
- Separate concurrent database connections verify exactly one successful initial bootstrap, exactly one successful stock issue when two would overspend, and exactly-once posting under two simultaneous identical requests.
- Queries under the `authenticated` API role verify workspace read isolation, private attachment read isolation, permitted own-workspace upload/denied foreign-workspace upload, and denial of direct stock-ledger writes.
- BigDecimal domain tests cover kg/g conversion, rejecting mass/volume guesses, 23 × 8 = 184, upward thaal rounding, buffer scaling, shared demand shortage, nonnegative shortages and invalid yield.

The backend test fixture emulates `auth.uid()`, Auth users, Storage tables and a Realtime publication. PostgreSQL warns that its local wal_level does not support logical delivery; publication creation is tested, Realtime transport is not. Test data is isolated and discarded. These checks do not validate the actual Supabase Auth/Storage HTTP services.

## Hosted checks and external setup

A read-only request to the supplied project's `workspace_snapshot` RPC returned HTTP 404 / PGRST202: the function was absent from the Data API schema cache. Neither migration deployment nor a successful app backend session has been claimed. Only a publishable key was provided; no project-admin/DB credentials were available to deploy migrations.

No email was sent to another person. SMTP, code templates, verified sign-in, real invitations, expired sessions, hosted Storage and Realtime require the setup in `superbase.md` and a real two-user acceptance run. No authoritative Bohri calendar dataset or redistribution permission was established. No calendar date is silently invented.

## Device checks

Android emulator startup was attempted twice using the available API 35 AVD, including a reduced-memory software renderer configuration. The emulator process exited with a segmentation fault before a stable boot. Therefore APK launch, screenshots, large text, dark/light layouts, touch targets, tablet adaptation, process-death restoration and Android share/file-picker interactions are **not verified on a device**. APK build success is not a substitute for those checks. No release-signing credential was supplied; the installable APK uses a development debug signature verified with apksigner. An unsigned release APK also builds successfully and is included for operator signing. The opt-in development seed was separately executed successfully against the local PostgreSQL platform fixture.

## Acceptance scenarios

| Brief scenario | Evidence and current limit |
|---|---|
| Secretary claims workspace and invites member | Database bootstrap, invitation and owner checks pass; real email/device flow pending |
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

Additional local assertions cover FEFO expiry ordering, reconciliation and reversal, advance settlement limits, partial reimbursements, partial production, additional production after completion, remaining demand and preserved recipe snapshots. Calendar override conflicts, receipt upload metadata and Room/WorkManager lifecycle paths are not comprehensively tested. Verify these before relying on them operationally.

## Incomplete brief requirements

- No purchase orders, manual shopping lines, confirmed incoming quantity management, invoice tax/discount/extra charges, invoice cancellation or invoice editing/reversal workflow. Only original invoice lines, receipts, returns and payments are supported. Shopping shortages do not deduct unreceived invoice quantities or purchase-order promises.
- No item aliases/conversion editor, purchasing pack UI or configurable tiffin/thaal portion profiles. Entry uses canonical ingredient units. `item_conversions` is schema preparation only. Recipe overrides, production consumption reversal and full amendment/correction chains are not complete.
- No per-service production demand override, recommendation engine, forecast from historical waste, per-event actual-cost summary, per-tiffin cost report, reconciliation report or date-range reporting/search/filter suite. Journal CSV and underlying consumption costs exist.
- Menu plans are a chronological service list; no weekly grid, full-week copying, holiday calendar or service suspension configuration. Copying an individual service, cancellation and multiple services per date are supported.
- Calendar uses manually maintained mappings with source/verification; no automatic conversion, licensed import, calendar provider plugin interface, supported continuous date-range guarantee, month grid or automatic community observance feed.
- Basic tasks, notes and activity exist; no in-app notification inbox/preferences, push, automated invitation mail, OCR or reviewed import UI.
- Kitchen defaults and a record-derived setup checklist are present; a full staged/resumable onboarding wizard is not. One active workspace is selected automatically; multi-workspace selection is not provided.
- UI strings have not all been extracted for localization. Adaptive tablet layouts and comprehensive accessibility/device QA are pending. A single snapshot RPC loads all records, so large historical datasets need pagination.

Use a development project and evaluation data until critical remaining workflows have been tested and the relevant missing features are completed. See `docs/operator-guide.md` for supported procedures and `superbase.md` for deployment.
