package org.vasaigaam.fmb.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.vasaigaam.fmb.R
import org.vasaigaam.fmb.data.*
import org.vasaigaam.fmb.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun calendarOverrides(data:JsonObject):List<CalendarOverride> = data.rows("calendar_mappings").mapNotNull { row->
 runCatching { CalendarOverride(LocalDate.parse(row.str("gregorian")),row.str("hijri"),row.str("source"),row.str("verified")=="true") }.getOrNull()
}
private fun statusResource(date:ResolvedBohriDate)=when {
 date.isVerified->R.string.calendar_verified
 date.override!=null->R.string.calendar_unverified_override
 date.calculated==null->R.string.calendar_unavailable
 else->R.string.calendar_calculated
}
@Composable fun hijriText(date:MisriDate?):String {
 val months=stringArrayResource(R.array.calendar_misri_months)
 return date?.let{"${it.day} ${months[it.month-1]} ${it.year} AH"}?:stringResource(R.string.calendar_unavailable)
}
@Composable private fun rememberCalendarToday():LocalDate {
 var today by remember{mutableStateOf(BohriCalendar.today())}
 LaunchedEffect(Unit){while(true){delay(60000);today=BohriCalendar.today()}}
 return today
}
@Composable fun currentBohriLabel(data:JsonObject):String {
 val today=rememberCalendarToday()
 val resolved=BohriCalendar().resolve(today,calendarOverrides(data))
 val label=resolved.override?.hijri?:hijriText(resolved.calculated)
 val status=stringResource(statusResource(resolved))
 return if(resolved.calculated==null&&resolved.override==null)label else "$label\n$status"
}

@Composable fun BohriCalendarPanel(data:JsonObject,owner:Boolean,onEditMapping:(JsonObject)->Unit,onPlan:(JsonObject)->Unit,onOpenSource:()->Unit,initialDate:LocalDate=BohriCalendar.today()) {
 val calendar=remember{BohriCalendar()}
 val today=rememberCalendarToday()
 var selectedIso by rememberSaveable{mutableStateOf(initialDate.toString())}
 var misriMode by rememberSaveable{mutableStateOf(true)}
 var jump by remember{mutableStateOf(false)}
 var dateText by remember{mutableStateOf("")}
 var dateError by remember{mutableStateOf(false)}
 val selected=LocalDate.parse(selectedIso)
 val overrides=remember(data){calendarOverrides(data)}
 val resolved=calendar.resolve(selected,overrides)
 val days=calendar.monthDays(selected,misriMode)
 val previous=calendar.adjacentMonth(selected,-1,misriMode)
 val next=calendar.adjacentMonth(selected,1,misriMode)
 val months=stringArrayResource(R.array.calendar_misri_months)
 val weekdays=stringArrayResource(R.array.calendar_weekdays)
 val locale=Locale.forLanguageTag("en-IN")
 val civilFormat=remember{DateTimeFormatter.ofPattern("dd MMM yyyy",locale)}
 val monthTitle=if(misriMode&&resolved.calculated!=null)"${months[resolved.calculated.month-1]} ${resolved.calculated.year} AH" else selected.format(DateTimeFormatter.ofPattern("MMMM yyyy",locale))
 val services=data.rows("services")
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text(stringResource(R.string.calendar_title),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
  Text(stringResource(R.string.calendar_description),style=MaterialTheme.typography.bodyMedium)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   FilterChip(selected=misriMode,onClick={misriMode=true},label={Text(stringResource(R.string.calendar_misri_mode))})
   FilterChip(selected=!misriMode,onClick={misriMode=false},label={Text(stringResource(R.string.calendar_gregorian_mode))})
   TextButton(onClick={selectedIso=today.toString()}){Text(stringResource(R.string.calendar_today))}
  }
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
   IconButton(onClick={previous?.let{selectedIso=it.toString()}},enabled=previous!=null){Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft,stringResource(R.string.calendar_previous))}
   Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
    Text(monthTitle,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    Text("${days.first().format(civilFormat)} – ${days.last().format(civilFormat)}",style=MaterialTheme.typography.labelSmall)
   }
   IconButton(onClick={next?.let{selectedIso=it.toString()}},enabled=next!=null){Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,stringResource(R.string.calendar_next))}
  }
  Row(Modifier.fillMaxWidth()){weekdays.forEach{Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelMedium)}}
  val padding=days.first().dayOfWeek.value%7
  val cells=List<LocalDate?>(padding){null}+days
  cells.chunked(7).forEach { week->
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
    (week+List<LocalDate?>(7-week.size){null}).forEach{day->
     if(day==null)Spacer(Modifier.weight(1f)) else {
      val cell=calendar.resolve(day,overrides)
      val mapped=cell.override
      val selectedDay=day==selected
      val cellHijri=mapped?.hijri?.let{Regex("^([0-9]{1,2})(?:\\s|$)").find(it)?.groupValues?.get(1)}?:if(mapped!=null)"*" else cell.calculated?.day?.toString()?:"–"
      val main=if(misriMode)cellHijri else day.dayOfMonth.toString()
      val secondary=if(misriMode)day.format(DateTimeFormatter.ofPattern("d MMM",locale)) else cellHijri
      val count=services.count{it.str("service_date")==day.toString()&&it.str("status")!="cancelled"}
      val fullHijri=mapped?.hijri?:hijriText(cell.calculated)
      val provenance=stringResource(statusResource(cell))
      Surface(onClick={selectedIso=day.toString()},modifier=Modifier.weight(1f).heightIn(min=76.dp).semantics(mergeDescendants=true){contentDescription="${day.format(civilFormat)}; $fullHijri; $provenance";this.selected=selectedDay},shape=RoundedCornerShape(10.dp),color=if(selectedDay)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,border=if(day==today)BorderStroke(1.dp,MaterialTheme.colorScheme.primary)else null) {
       Column(Modifier.padding(horizontal=1.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(main,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Text(secondary,style=MaterialTheme.typography.labelSmall,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        Text(if(mapped!=null)"*" else if(count>0)"•" else " ",style=MaterialTheme.typography.labelSmall)
       }
      }
     }
    }
   }
  }
  Text(stringResource(R.string.calendar_legend),style=MaterialTheme.typography.labelSmall)
  OutlinedButton(onClick={dateText=selectedIso;dateError=false;jump=true}){Text(stringResource(R.string.calendar_jump))}
  Card(Modifier.fillMaxWidth()) {
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Text(selected.format(civilFormat),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    Text(resolved.override?.hijri?:hijriText(resolved.calculated),style=MaterialTheme.typography.titleLarge)
    if(resolved.calculated!=null||resolved.override!=null)Text(stringResource(statusResource(resolved)),style=MaterialTheme.typography.labelMedium)
    Text(resolved.override?.source?:SourceMisriCalendar.source,style=MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.calendar_plans),style=MaterialTheme.typography.titleSmall)
    val plans=services.filter{it.str("service_date")==selectedIso}.sortedBy{it.str("title")}
    if(plans.isEmpty())Text(stringResource(R.string.calendar_no_events))
    plans.forEach { row->
     val kind=when(row.str("kind")){"observance"->R.string.calendar_observance;"gathering"->R.string.calendar_gathering;else->R.string.calendar_tiffin}
     Text("${row.str("title")}\n${stringResource(kind)} · ${row.str("status")}",style=MaterialTheme.typography.bodyMedium)
    }
    OutlinedButton(onClick={onPlan(buildJsonObject{put("service_date",selectedIso);put("kind","observance");put("planned","0");put("confirmed","0");put("buffer","0");put("people_per_thaal","8")})}){Text(stringResource(R.string.calendar_plan))}
    if(owner)TextButton(onClick={
     onEditMapping(data.rows("calendar_mappings").find{it.str("gregorian")==selectedIso}?:buildJsonObject{put("gregorian",selectedIso);put("hijri",resolved.calculated?.let{"${it.day} ${months[it.month-1]} ${it.year} AH"}.orEmpty());put("source",SourceMisriCalendar.source);put("verified","false")})
    }){Text(stringResource(R.string.calendar_correct))}
   }
  }
  Text(stringResource(R.string.calendar_range),style=MaterialTheme.typography.bodySmall)
  Text(stringResource(R.string.calendar_boundaries),style=MaterialTheme.typography.bodySmall)
  Text(stringResource(R.string.calendar_source_credit),style=MaterialTheme.typography.bodySmall)
  TextButton(onClick=onOpenSource){Text(stringResource(R.string.calendar_open_source))}
  Text(stringResource(R.string.calendar_mapping_history),style=MaterialTheme.typography.titleMedium)
  val mappings=data.rows("calendar_mappings").sortedBy{it.str("gregorian")}
  if(mappings.isEmpty())Text(stringResource(R.string.calendar_mapping_empty),style=MaterialTheme.typography.bodySmall)
  mappings.forEach { row->
   ListItem(headlineContent={Text("${row.str("gregorian")} · ${row.str("hijri")}")},supportingContent={Text("${if(row.str("verified")=="true")stringResource(R.string.calendar_verified)else stringResource(R.string.calendar_unverified_override)}\n${row.str("source")}")})
   if(owner)TextButton(onClick={onEditMapping(row)}){Text(stringResource(R.string.calendar_mapping_edit))}
  }
 }
 if(jump)AlertDialog(onDismissRequest={jump=false},title={Text(stringResource(R.string.calendar_jump))},text={OutlinedTextField(dateText,{dateText=it;dateError=false},label={Text(stringResource(R.string.calendar_date_prompt))},isError=dateError,supportingText=if(dateError)({Text(stringResource(R.string.calendar_date_invalid))})else null,singleLine=true)},confirmButton={TextButton(onClick={val date=runCatching{LocalDate.parse(dateText.trim())}.getOrNull();if(date==null)dateError=true else{selectedIso=date.toString();if(SourceMisriCalendar.fromGregorian(date)==null)misriMode=false;jump=false}}){Text(stringResource(R.string.calendar_go))}},dismissButton={TextButton(onClick={jump=false}){Text(stringResource(R.string.calendar_cancel))}})
}
