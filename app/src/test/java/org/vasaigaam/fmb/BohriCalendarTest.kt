package org.vasaigaam.fmb

import org.junit.Assert.*
import org.junit.Test
import org.vasaigaam.fmb.domain.*
import java.time.*

class BohriCalendarTest {
 private val source=SourceMisriCalendar
 @Test fun sourceAnchorMatches(){assertEquals(MisriDate(1446,1,1),source.fromGregorian(LocalDate.of(2024,7,7)))}
 @Test fun sourceDisplayedRamadanDatesMatch(){
  assertEquals(MisriDate(1447,9,1),source.fromGregorian(LocalDate.of(2026,2,17)))
  assertEquals(MisriDate(1447,9,30),source.fromGregorian(LocalDate.of(2026,3,18)))
 }
 @Test fun leapYearIncludesLastDayAndRollsOver(){
  assertEquals(30,source.daysInMonth(1447,12))
  val end=source.toGregorian(MisriDate(1447,12,30))
  assertEquals(MisriDate(1448,1,1),source.fromGregorian(end.plusDays(1)))
  assertEquals(29,source.daysInMonth(1446,12))
 }
 @Test fun allSupportedDatesRoundTrip(){
  var date=source.firstDate
  while(date<=source.lastDate){assertEquals(date,source.toGregorian(requireNotNull(source.fromGregorian(date))));date=date.plusDays(1)}
 }
 @Test fun outOfRangeIsExplicitInsteadOfRepeatingAnInventedDate(){
  assertNull(source.fromGregorian(source.firstDate.minusDays(1)))
  assertNull(source.fromGregorian(source.lastDate.plusDays(1)))
 }
 @Test fun invalidMonthDayRejected(){assertThrows(IllegalArgumentException::class.java){source.toGregorian(MisriDate(1446,12,30))}}
 @Test fun verifiedOverrideWinsAndKeepsProvenance(){
  val day=LocalDate.of(2026,2,17)
  val record=CalendarOverride(day,"Reviewed date","Committee register",true)
  val result=BohriCalendar().resolve(day,listOf(record))
  assertEquals(record,result.override);assertTrue(result.isVerified);assertEquals(MisriDate(1447,9,1),result.calculated)
 }
 @Test fun calculatedAndUnverifiedOverrideAreNeverMarkedVerified(){
  val day=LocalDate.of(2026,2,17)
  assertFalse(BohriCalendar().resolve(day,emptyList()).isVerified)
  assertFalse(BohriCalendar().resolve(day,listOf(CalendarOverride(day,"Pending","Source",false))).isVerified)
 }
 @Test fun indiaMidnightChangesDateWithoutUsingDeviceTimezone(){
  assertEquals(LocalDate.of(2026,10,20),BohriCalendar.today(Clock.fixed(Instant.parse("2026-10-19T18:30:00Z"),ZoneOffset.UTC)))
  assertEquals(LocalDate.of(2026,10,19),BohriCalendar.today(Clock.fixed(Instant.parse("2026-10-19T18:29:59Z"),ZoneOffset.UTC)))
 }
 @Test fun misriMonthGridIncludesLeapDayAndHasCorrectNextMonth(){
  val end=source.toGregorian(MisriDate(1447,12,30));val calendar=BohriCalendar()
  assertEquals(30,calendar.monthDays(end,true).size)
  assertEquals(source.toGregorian(MisriDate(1448,1,1)),calendar.adjacentMonth(end,1,true))
  assertNull(calendar.adjacentMonth(source.firstDate,-1,true))
 }
 @Test fun gregorianFebruaryAndUnavailableEarlyDatesStillRender(){
  assertEquals(28,BohriCalendar().monthDays(LocalDate.of(2026,2,17),false).size)
  assertEquals(31,BohriCalendar().monthDays(LocalDate.of(2024,7,1),false).size)
 }
}
