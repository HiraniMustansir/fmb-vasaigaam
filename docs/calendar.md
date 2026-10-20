# Bohri calendar integration

Version 0.3.0 adds a native calendar under **Miqaats → Calendar** and today's Hijri date on Home. It works offline. No additional database migration or external account is required beyond the app's existing setup.

## Reference and method

The user-selected reference is [dawoodibohracalendar.in](https://dawoodibohracalendar.in/), inspected on 20 October 2026. The site performs client-side Misri tabular calculation. No published data API or redistribution licence was found in the inspected page/assets. The app does not bundle the site's JavaScript, scrape its event catalogue or call an invented endpoint.

An independent Kotlin implementation uses these mathematical parameters observed in the reference's converter:

- 1 Muharram 1446 corresponds to 7 July 2024.
- Months alternate 30 and 29 days.
- Leap years have residues 2, 5, 7, 10, 13, 16, 18, 21, 24, 26 and 29 in the 30-year cycle. Month 12 has 30 days in those years.
- Two displayed reference checks: 1 Ramadan 1447 = 17 February 2026; 30 Ramadan 1447 = 18 March 2026.

This is a source-referenced calculation, not a claim of religious/community authority. A calculated date is never automatically marked committee-verified. The local provider deliberately returns unavailable before its anchor instead of reproducing the reference converter's pre-anchor fallback. Leap-month grids include all 30 days where applicable.

## Supported range and date boundaries

The app's chosen calculation range is **1446–1500 AH**, inclusive. That is a software support range, not a claim that the source has verified every future date. Gregorian calendar cells outside that range explicitly show that Hijri calculation is unavailable. The provider can be replaced through `BohriCalendarProvider` if an authorized feed or authoritative conversion service becomes available.

Dates are civil dates in **Asia/Kolkata**, changing at midnight. No automatic sunset rollover is applied. Religious observance boundaries must be confirmed through appropriate community guidance. All arithmetic uses `LocalDate` and integer day counts, avoiding timezone/DST differences in date conversion.

The reference parameters are bundled, so the calculation works without a connection. Changes to the source website do not automatically update the native provider; they require review and a tested app update. The source button opens the current public website in the user's browser when explicitly tapped.

## UI and committee plans

- Misri and Gregorian month modes, previous/next month, Today and a Gregorian-date jump control.
- Every day displays both calendars; the selected day displays the full Hijri date and its provenance.
- A dot marks a date with committee plans. Selecting a date shows its existing tiffin services, gatherings and observance-only records.
- Add Miqaat or gathering starts the existing service form on the selected date, defaulting to **observance only**, with zero food demand. The user can deliberately change it to a food gathering.
- The source's live observance listings are reached using **Open source calendar & observances**. They are not silently imported, represented as local events or converted into kitchen demand.
- Newly added calendar UI text and month/day names are stored in Android string resources for localization.

## Corrections and provenance

The existing `calendar_mappings` table remains the correction store. The owner can select a date and choose **Correct selected date**, recording the Hijri label, source/review note and explicit verification state. Verified and unverified manual corrections take precedence over the calculated label in the grid, selected-day details and Home. An asterisk indicates a manual correction; full details appear when selected.

Overrides are individual-date labels, not a new month-boundary algorithm. Month navigation remains based on the calculated provider; a manual label does not silently shift all surrounding dates. This preserves existing free-text historical mappings and avoids inferring unreviewed dates from one correction.

Updates retain optimistic record versions and audit history. A stale update is rejected, not silently applied. A date correction does not change the Gregorian date of an already scheduled event. Members can read the same corrections through the existing snapshot, Realtime invalidation and Room read cache. Only the owner maintains verification mappings; changing one's descriptive position does not grant that authority.

## Validation

`BohriCalendarTest` checks source anchors, both conversion directions across every supported day, leap-day/year rollover, invalid dates, out-of-range handling, manual override precedence and verification, month navigation and the India midnight boundary.

`BohriCalendarUiTest` runs on the connected physical phone using isolated test UI data. It verifies the source-referenced month, Gregorian mode, month navigation, verified corrections, unavailable earlier dates, observance-only planning defaults and an explicitly initiated source action. It does not sign in, send credentials to the source or post committee records.

`supabase/tests/calendar_overrides.sql` checks correction versions, conflict rejection, audit preservation, owner-only verification administration and shared member reads. These PostgreSQL checks use the local platform fixture, not the hosted project.

Remaining external verification: community approval of the reference's date convention; full hosted two-member synchronization; live browser loading of the external source; and a licensed/authorized event data feed if automatic observance import is desired.
