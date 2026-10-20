package org.vasaigaam.fmb.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import org.vasaigaam.fmb.*
import org.vasaigaam.fmb.data.*
import org.vasaigaam.fmb.domain.Reports
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.math.RoundingMode

@Composable fun WorkspaceSummary(data:JsonObject,draftCount:Int,onTasks:()->Unit,onDrafts:()->Unit) {
 val today=LocalDate.now(Reports.zone).toString()
 val planned=data.rows("services").count{it.str("service_date")==today&&it.str("status") !in listOf("cancelled","completed")}
 val tasks=data.rows("tasks").count{it.str("done")!="true"}
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  SummaryTile("Today’s plans",planned.toString(),Modifier.weight(1f))
  SummaryTile("Open tasks",tasks.toString(),Modifier.weight(1f),onTasks)
  SummaryTile("Drafts",draftCount.toString(),Modifier.weight(1f),onDrafts)
 }
}
@Composable private fun SummaryTile(label:String,value:String,modifier:Modifier,onClick:(()->Unit)?=null) {
 val content:@Composable ()->Unit={Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(label,style=MaterialTheme.typography.labelMedium)}}
 if(onClick==null) Card(modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer),content={content()})
 else Card(onClick=onClick,modifier=modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer),content={content()})
}

@Composable fun WeekPicker(start:LocalDate,onChange:(LocalDate)->Unit) {
 val format=DateTimeFormatter.ofPattern("d MMM")
 Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
  Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   IconButton(onClick={onChange(start.minusWeeks(1))}){Icon(Icons.Outlined.ChevronLeft,"Previous week")}
   Column(horizontalAlignment=Alignment.CenterHorizontally) {
    Text("${start.format(format)} – ${start.plusDays(6).format(format)} ${start.year}",fontWeight=FontWeight.SemiBold)
    TextButton(onClick={onChange(Reports.weekStart(LocalDate.now(Reports.zone)))}){Text("This week")}
   }
   IconButton(onClick={onChange(start.plusWeeks(1))}){Icon(Icons.Outlined.ChevronRight,"Next week")}
  }
 }
}

@Composable fun FinancialReport(data:JsonObject) {
 val today=LocalDate.now(Reports.zone)
 var from by rememberSaveable{mutableStateOf(today.withDayOfMonth(1).toString())}
 var to by rememberSaveable{mutableStateOf(today.toString())}
 val range=runCatching{LocalDate.parse(from) to LocalDate.parse(to)}.getOrNull()
 val report=range?.takeIf{!it.second.isBefore(it.first)}?.let{Reports.period(data,it.first,it.second)}
 Heading("Kitchen reports","Posted ledger totals • India dates • INR")
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)) {
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   OutlinedTextField(from,{from=it},label={Text("From • YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth())
   OutlinedTextField(to,{to=it},label={Text("To • YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth())
   if(report==null) Text("Enter valid dates with the end on or after the start.",color=MaterialTheme.colorScheme.error)
   else {
    ReportValue("Contributions",report.income)
    ReportValue("Operating expenses",report.operating)
    ReportValue("Ingredients consumed",report.ingredients)
    HorizontalDivider()
    ReportValue("Net cash + bank change",report.cashChange)
   }
   Text("Totals include reversals posted during this period. Cash change includes payments, advances and opening entries; it is not profit. Ingredient cost is separate from operating expenses.",style=MaterialTheme.typography.bodySmall)
  }
 }
 Spacer(Modifier.height(16.dp))
 Heading("Service ingredient costs","All-time actual stock consumption, grouped by service. Includes additional cooking postings; excludes overheads.")
 val completed=data.rows("services").filter{s->data.rows("production_runs").any{it.str("service_id")==s.str("id")}}.sortedByDescending{it.str("service_date")}
 if(completed.isEmpty()) Empty("Costs appear after actual cooking has been recorded.")
 completed.forEach{s->
  val cost=Reports.serviceIngredientCost(data,s.str("id"))
  val prepared=data.rows("production_runs").filter{it.str("service_id")==s.str("id")}.fold(java.math.BigDecimal.ZERO){a,r->a+r.num("prepared")}
  Spacer(Modifier.height(8.dp))
  Record(s.str("title"),"${s.str("service_date")} • ${currency(cost)} ingredients\n${fmt(prepared)} ${if(s.str("kind")=="gathering")"thaals" else "tiffins"} prepared"+if(prepared.signum()>0)" • ${currency(cost.divide(prepared,2,RoundingMode.HALF_UP))} per prepared unit" else "")
 }
}
private fun currency(value:java.math.BigDecimal)=java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("en-IN")).format(value)
@Composable private fun ReportValue(label:String,value:java.math.BigDecimal) { Column {Text(label,style=MaterialTheme.typography.labelLarge);Text(currency(value),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)} }

@Composable fun ServiceRecord(title:String,date:String,status:String,detail:String,actions:@Composable RowScope.()->Unit) {
 Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
  Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text(date,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
   Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
   Surface(color=if(status=="completed")MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(8.dp)) {
    Text(status.replaceFirstChar{it.uppercase()},Modifier.padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium)
   }
   Text(detail,style=MaterialTheme.typography.bodyMedium)
   HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
   Row(Modifier.fillMaxWidth().then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())),content=actions)
  }
 }
}
