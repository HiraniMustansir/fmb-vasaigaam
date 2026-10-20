package org.vasaigaam.fmb.domain

import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class MisriDate(val year:Int,val month:Int,val day:Int)

/** A replaceable civil-date provider. It never uses Android's default Islamic chronology. */
interface BohriCalendarProvider {
 val source:String
 val firstDate:LocalDate
 val lastDate:LocalDate
 fun fromGregorian(date:LocalDate):MisriDate?
 fun toGregorian(date:MisriDate):LocalDate
 fun daysInMonth(year:Int,month:Int):Int
}

/** Independently implemented from the source's mathematical parameters; no site code or event list is bundled. */
object SourceMisriCalendar:BohriCalendarProvider {
 override val source="https://dawoodibohracalendar.in/"
 const val FIRST_YEAR=1446
 const val LAST_YEAR=1500
 override val firstDate:LocalDate=LocalDate.of(2024,7,7)
 private val leapYears=setOf(2,5,7,10,13,16,18,21,24,26,29)
 fun isLeapYear(year:Int)=Math.floorMod(year,30) in leapYears
 private fun yearLength(year:Int)=if(isLeapYear(year))355 else 354
 override val lastDate:LocalDate=firstDate.plusDays((FIRST_YEAR..LAST_YEAR).sumOf{yearLength(it)}.toLong()-1)
 override fun daysInMonth(year:Int,month:Int):Int {
  require(year in FIRST_YEAR..LAST_YEAR&&month in 1..12){"Date is outside the supported Misri range."}
  return if(month%2==1 || month==12&&isLeapYear(year))30 else 29
 }
 override fun fromGregorian(date:LocalDate):MisriDate? {
  if(date<firstDate||date>lastDate)return null
  var days=ChronoUnit.DAYS.between(firstDate,date).toInt()
  var year=FIRST_YEAR
  while(days>=yearLength(year)){days-=yearLength(year);year++}
  var month=1
  while(days>=daysInMonth(year,month)){days-=daysInMonth(year,month);month++}
  return MisriDate(year,month,days+1)
 }
 override fun toGregorian(date:MisriDate):LocalDate {
  val monthDays=daysInMonth(date.year,date.month)
  require(date.day in 1..monthDays){"Invalid day for this Misri month."}
  val years=(FIRST_YEAR until date.year).sumOf{yearLength(it)}
  val months=(1 until date.month).sumOf{daysInMonth(date.year,it)}
  return firstDate.plusDays((years+months+date.day-1).toLong())
 }
}

data class CalendarOverride(val date:LocalDate,val hijri:String,val source:String,val verified:Boolean)
data class ResolvedBohriDate(val gregorian:LocalDate,val calculated:MisriDate?,val override:CalendarOverride?) {
 val isVerified get()=override?.verified==true
}
class BohriCalendar(private val provider:BohriCalendarProvider=SourceMisriCalendar) {
 fun resolve(date:LocalDate,overrides:List<CalendarOverride>)=ResolvedBohriDate(date,provider.fromGregorian(date),overrides.find{it.date==date})
 fun monthDays(selected:LocalDate,misri:Boolean):List<LocalDate> {
  val current=provider.fromGregorian(selected)
  if(misri&&current!=null){
   val start=provider.toGregorian(current.copy(day=1))
   return List(provider.daysInMonth(current.year,current.month)){start.plusDays(it.toLong())}
  }
  val month=YearMonth.from(selected)
  return List(month.lengthOfMonth()){month.atDay(it+1)}
 }
 fun adjacentMonth(selected:LocalDate,offset:Int,misri:Boolean):LocalDate? {
  require(offset==1||offset== -1)
  val current=provider.fromGregorian(selected)
  if(misri&&current!=null){
   val serial=current.year*12+current.month-1+offset
   val year=Math.floorDiv(serial,12);val month=Math.floorMod(serial,12)+1
   return runCatching{provider.toGregorian(MisriDate(year,month,1))}.getOrNull()
  }
  return selected.withDayOfMonth(1).plusMonths(offset.toLong()).takeIf{it<=provider.lastDate&&YearMonth.from(it)>=YearMonth.from(provider.firstDate)}
 }
 companion object {
  val zone:ZoneId=ZoneId.of("Asia/Kolkata")
  fun today(clock:Clock=Clock.systemUTC()):LocalDate=LocalDate.now(clock.withZone(zone))
 }
}
