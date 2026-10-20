package org.vasaigaam.fmb.domain

import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

/** Derived views only: reports never post transactions or estimate costs from invoice totals. */
object Reports {
 private fun JsonObject.text(key:String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
 private fun JsonObject.decimal(key:String)=text(key).toBigDecimalOrNull()?:BigDecimal.ZERO
 private fun JsonObject.rows(key:String)=(this[key] as? JsonArray)?.mapNotNull{it as? JsonObject}.orEmpty()
 val zone:ZoneId=ZoneId.of("Asia/Kolkata")
 fun postingDate(timestamp:String):LocalDate?=runCatching { Instant.parse(timestamp).atZone(zone).toLocalDate() }.getOrNull()
 fun weekStart(day:LocalDate):LocalDate=day.minusDays((day.dayOfWeek.value-1).toLong())
 fun balance(data:JsonObject,code:String):BigDecimal=data.rows("journal_lines").filter{it.text("account_code")==code}.fold(BigDecimal.ZERO){a,l->a+l.decimal("debit")-l.decimal("credit")}
 data class Period(val income:BigDecimal,val operating:BigDecimal,val ingredients:BigDecimal,val cashChange:BigDecimal)
 fun period(data:JsonObject,from:LocalDate,to:LocalDate):Period {
  require(!to.isBefore(from)){"End date must be on or after start date"}
  val ids=data.rows("journal_entries").filter{e->postingDate(e.text("created_at"))?.let{!it.isBefore(from)&&!it.isAfter(to)}==true}.map{it.text("id")}.toSet()
  val lines=data.rows("journal_lines").filter{it.text("entry_id") in ids}
  fun net(vararg codes:String)=lines.filter{it.text("account_code") in codes}.fold(BigDecimal.ZERO){a,l->a+l.decimal("debit")-l.decimal("credit")}
  return Period(-net("income"),net("expense"),net("food"),net("cash","bank"))
 }
 fun serviceIngredientCost(data:JsonObject,id:String):BigDecimal=data.rows("stock_movements").filter{it.text("service_id")==id&&it.text("kind")=="consumption"}.fold(BigDecimal.ZERO){a,m->a+(-m.decimal("quantity")*m.decimal("unit_cost")).setScale(2,java.math.RoundingMode.HALF_UP)}
 fun csvCell(value:String):String {
  // Neutralize spreadsheet formulas in user-entered titles/descriptions.
  val safe=if(value.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r','\n')) "'"+value else value
  return "\""+safe.replace("\"","\"\"")+"\""
 }
}
