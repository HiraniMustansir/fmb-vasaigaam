package org.vasaigaam.fmb

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.vasaigaam.fmb.domain.Reports
import java.time.LocalDate
import java.math.BigDecimal

class ReportsTest {
 private val data=Json.parseToJsonElement("""{
  "journal_entries":[{"id":"a","created_at":"2026-10-19T20:00:00Z"},{"id":"b","created_at":"2026-10-20T12:00:00Z"},{"id":"c","created_at":"2026-10-21T00:00:00Z"}],
  "journal_lines":[
   {"entry_id":"a","account_code":"income","credit":100,"debit":0},
   {"entry_id":"a","account_code":"cash","credit":0,"debit":100},
   {"entry_id":"b","account_code":"income","credit":0,"debit":20},
   {"entry_id":"b","account_code":"cash","credit":20,"debit":0},
   {"entry_id":"b","account_code":"expense","debit":30,"credit":0},
   {"entry_id":"b","account_code":"food","debit":40,"credit":0},
   {"entry_id":"c","account_code":"income","credit":999,"debit":0}
  ],
  "stock_movements":[
   {"service_id":"s","kind":"consumption","quantity":-2,"unit_cost":12.5},
   {"service_id":"s","kind":"consumption","quantity":-1,"unit_cost":10},
   {"service_id":"other","kind":"consumption","quantity":-8,"unit_cost":10},
   {"service_id":"s","kind":"opening","quantity":100,"unit_cost":3}
  ]
 }""").jsonObject
 @Test fun indiaDateIncludesPreviousUtcEvening(){assertEquals(LocalDate.of(2026,10,20),Reports.postingDate("2026-10-19T20:00:00Z"))}
 @Test fun periodIncludesReversalAndBothBoundaries(){
  val r=Reports.period(data,LocalDate.of(2026,10,20),LocalDate.of(2026,10,20))
  assertEquals(BigDecimal("80"),r.income);assertEquals(BigDecimal("30"),r.operating);assertEquals(BigDecimal("40"),r.ingredients);assertEquals(BigDecimal("80"),r.cashChange)
 }
 @Test fun costsIncludeAmendmentsButExcludeOtherServicesAndReceipts(){assertEquals(0,BigDecimal("35").compareTo(Reports.serviceIngredientCost(data,"s")))}
 @Test fun missingRecordsGiveZero(){assertEquals(BigDecimal.ZERO,Reports.balance(JsonObject(emptyMap()),"cash"))}
 @Test fun weeksStartOnMondayAcrossYear(){assertEquals(LocalDate.of(2025,12,29),Reports.weekStart(LocalDate.of(2026,1,1)))}
 @Test(expected=IllegalArgumentException::class) fun rejectsBackwardsPeriod(){Reports.period(data,LocalDate.of(2026,10,21),LocalDate.of(2026,10,20))}
 @Test fun neutralizesCsvFormulasAndEscapesQuotes(){assertEquals("\"'=HYPERLINK(\"\"x\"\")\"",Reports.csvCell("=HYPERLINK(\"x\")"));assertEquals("\"Plain title\"",Reports.csvCell("Plain title"))}
}
