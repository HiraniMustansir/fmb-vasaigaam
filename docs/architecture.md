# Implementation plan and data model

This repository began empty. The attached brief was used as a requirements reference; it was not executable repository instructions.

The implementation order was: password identity and workspace boundary; recipes/menu/demand; stock and purchasing; journals and production; supporting collaboration/calendar; local builds and database tests; APK packaging. A single native application is connected to Supabase RPCs, not a web wrapper or a static demo dashboard.

## Responsibilities

Compose screens collect ViewModel StateFlow. Koin supplies a repository containing Supabase Auth, PostgREST, Storage and Realtime. Room stores per-user drafts and snapshots; WorkManager periodically revalidates membership. Domain calculations use BigDecimal; PostgreSQL quantities use NUMERIC. No authoritative stock/money computation uses binary floating point.

`workspace_snapshot()` returns the current user's first active workspace, after an Auth-account and current-membership check. `execute_command(kind,data,key)` validates membership on every call. Every mutation is a database transaction. A workspace row lock serializes operational mutations for this small committee. An advisory transaction lock serializes profile setup, creates the singleton workspace once and automatically enrolls subsequent users. Results are saved with actor, payload and UUID key; retries must match exactly. Draft keys survive network retries and process restarts.

Stock and journal tables are not directly writable by API roles. Composite foreign keys constrain related records to the same workspace. Active members have equal operational rights. Owner checks are limited to settings, revocation, ownership and maintained calendar mappings. Positions remain descriptive.

## Relationships

Workspace → memberships, invitations and operational records.

Item → recipe ingredients, invoice lines, movements and reservations. Recipe → version snapshots → service dishes → services → production runs. Tiffin and gathering demand aggregates through the same ingredient demand view. A gathering scales servings by thaals × people per thaal; recipes based on thaals scale by thaals. A tiffin-basis recipe treats one tiffin as one serving for gathering planning; operators must choose an appropriate recipe basis/recipe variant for their actual portions.

Supplier → invoice → invoice lines → partial receipt/return movements. Each receipt increases stock and transfers the corresponding value from purchases-awaiting-receipt to inventory. Each bill credits supplier payable. Payments debit payable and credit cash/bank. Neither a bill alone nor publishing a menu consumes stock.

Positive movements form physical lots. Negative movements allocate those lots by expiry, then creation time. Movement/allocation records preserve batch consumption. Valuation uses moving weighted-average cost across item stock; it is not lot-cost accounting. Production records preserve their recipes and consumption costs. Later recipe or price changes do not rewrite posted history.

Production can be partial (`complete=false`), complete, or a deliberate additional posting on a completed service (`amendment=true`, reason required). Outputs are incremental for each run. Corrections of consumption quantities and production reversal are not yet provided; see validation limits.

Journals contain two or more balanced lines, enforced by a deferred constraint trigger. Amounts use two decimal INR precision. Advances are assets until settled; member expenses establish reimbursement liabilities. Payment/settlement amounts cannot exceed the relevant unsettled balance. Stock movements retain their linked value journal for controlled reversal.

Calendar mappings store Gregorian date, supplied Bohri date, verification/source and version. Audit history records changes. No generic Islamic conversion is used. The calendar does not infer that an observance entails food demand.

## Connectivity and privacy

Read snapshots are cached for up to 24 hours. The app labels cached views and connection errors. Successful no-membership responses erase cached data and drafts. Sign-out clears local data. Foreground refresh runs every 30 seconds; Realtime improves latency. WorkManager checks while connected at approximately 15-minute intervals, subject to Android scheduling. An offline device cannot learn about a remote revocation immediately. Cache remains inaccessible through the app after sign-out and has Android sandbox protection; database encryption beyond device encryption is not implemented. Android cloud/device backups are disabled.

The app uses email/password authentication with Confirm email disabled. It sends no signup/sign-in email. Anyone who registers may complete their name/position and join the singleton workspace without approval. The first completed profile creates the workspace and becomes owner. Passwords remain transient UI/call memory; only Supabase stores password hashes. Old bootstrap/invitation commands are retired by migration 003. Email password recovery is unavailable without SMTP and requires an identity-checked project administrator. Push notifications are not implemented. Attachment access uses private Storage and a user-initiated signed URL valid for 60 seconds; an already-issued URL can remain usable until it expires.

## Extension boundaries

A dedicated, replaceable calendar provider should be introduced before adding automatic date conversion; the current provider is the maintained mapping table. For a larger dataset, paginate snapshots and activity, introduce typed feature repositories, and split UI files by feature. There is no background queue that silently posts financial or stock drafts.
