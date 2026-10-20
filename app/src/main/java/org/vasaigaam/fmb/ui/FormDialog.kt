package org.vasaigaam.fmb.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.vasaigaam.fmb.data.*
import java.util.UUID

@Composable fun FormDialog(form:Form,data:JsonObject,vm:FmbViewModel,existingId:String?,onDismiss:()->Unit) {
 val state by vm.state.collectAsState()
 val w=(data["workspace"] as? JsonObject)?.str("id").orEmpty()
 val values=remember(form) { mutableStateMapOf<String,String>().apply { form.fields.forEach { put(it.key,form.initial.str(it.key).ifEmpty{it.default.ifEmpty{it.options.firstOrNull()?.first.orEmpty()}}) } } }
 val lines=remember(form) { mutableStateListOf<JsonObject>().apply { addAll(form.initial.rows(form.lines.orEmpty())) } }
 val selected=remember(form) { mutableStateListOf<String>().apply { addAll((form.initial["recipe_ids"] as? JsonArray)?.map{it.jsonPrimitive.content}.orEmpty()) } }
 val key=rememberSaveable { existingId?:UUID.randomUUID().toString() }
 var validation by remember { mutableStateOf("") }
 var confirm by remember { mutableStateOf(false) }
 var submitting by remember { mutableStateOf(false) }
 val productionVersion=remember(values["service_id"]) { form.initial["version"]?:data.rows("services").find{it.str("id")==values["service_id"]}?.get("version")?:JsonPrimitive(1) }
 fun payload()=buildJsonObject {
  put("workspace_id",w)
  form.initial["id"]?.let{put("id",it)}
  form.initial["version"]?.let{put("version",it)}
  values.forEach{(k,v)->put(k,v)}
  if(form.kind=="settings") put("settings",buildJsonObject{values.forEach{(k,v)->put(k,v)}})
  if(form.kind=="production") { val s=data.rows("services").find{it.str("id")==values["service_id"]};put("version",productionVersion) }
  if(form.lines=="recipe_ids") put("recipe_ids",JsonArray(selected.map{JsonPrimitive(it)}))
  else form.lines?.let{put(it,JsonArray(lines.toList()))}
 }
 val current=payload().toString()
 val draft=Draft(key,vm.repo.user,w,form.kind,current)
 val canDraft=form.kind!="onboarding"
 LaunchedEffect(state.busy,state.error) { if(!state.busy&&state.error!=null)submitting=false }
 LaunchedEffect(current,submitting) { if(canDraft&&!submitting) { delay(700);vm.repo.save(draft) } }
 Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
  Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
   Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
    Text(form.title,Modifier.padding(20.dp),style=MaterialTheme.typography.headlineSmall)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
     if(canDraft) Text("Draft • Changes are saved on this device. Review before posting.",style=MaterialTheme.typography.labelMedium)
     if(form.kind=="stock") Text("Use negative quantities for waste and returns. A receipt requires an invoice line. Quantities use the item's base unit.")
     if(form.kind=="invoice") Text("Enter quantities and rates in item base units. Saving the bill does not receive goods or pay the supplier.")
     if(form.kind=="production") Text("Enter actual ingredients used, then outputs. Confirming consumes stock and completes this service.")
     if(form.kind=="revoke") Text("The member will immediately lose server access. Their local cache is cleared on the next connection.")
     if(form.kind=="onboarding") Text("Positions include Secretary, Treasurer, Joint Treasurer, Kitchen Coordinator, Purchase Coordinator and Committee Member. You can enter a custom position.")
     form.fields.forEach{field->
      if(field.options.isNotEmpty()) Choice(field.label,values[field.key].orEmpty(),field.options,field.optional){values[field.key]=it}
      else OutlinedTextField(value=values[field.key].orEmpty(),onValueChange={values[field.key]=it},label={Text(field.label+if(field.optional)" (optional)" else "")},modifier=Modifier.fillMaxWidth(),minLines=if(field.key in listOf("notes","instructions","body"))2 else 1)
     }
     if(form.lines=="recipe_ids") {
      Text("Dishes",style=MaterialTheme.typography.titleMedium)
      if(data.rows("recipes").isEmpty()) Text("Create recipes in Menu → Recipes first.")
      data.rows("recipes").forEach{r->Row{Checkbox(selected.contains(r.str("id")),{if(it)selected.add(r.str("id"))else selected.remove(r.str("id"))});Text(r.str("name"),Modifier.padding(top=12.dp))}}
     } else if(form.lines!=null) {
      Text(if(form.kind=="production")"Actual ingredients consumed" else "Ingredients / invoice lines",style=MaterialTheme.typography.titleMedium)
      lines.forEachIndexed{i,line->Card {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
       Choice("Ingredient",line.str("item_id"),data.rows("items").map{it.str("id") to "${it.str("name")} (${it.str("unit")})"}){v->lines[i]=buildJsonObject{line.forEach{(k,x)->put(k,x)};put("item_id",v)}}
       OutlinedTextField(line.str("quantity"),{v->lines[i]=buildJsonObject{line.forEach{(k,x)->put(k,x)};put("quantity",v)}},label={Text("Quantity in base units")},modifier=Modifier.fillMaxWidth())
       if(form.kind=="invoice") OutlinedTextField(line.str("unit_cost"),{v->lines[i]=buildJsonObject{line.forEach{(k,x)->put(k,x)};put("unit_cost",v)}},label={Text("Rate ₹ per base unit")},modifier=Modifier.fillMaxWidth())
       TextButton(onClick={lines.removeAt(i)}){Text("Remove line")}
      }}}
      OutlinedButton(onClick={lines.add(buildJsonObject{put("item_id",data.rows("items").firstOrNull()?.str("id").orEmpty());put("quantity","");if(form.kind=="invoice")put("unit_cost","")})},enabled=data.rows("items").isNotEmpty()){Text("Add ingredient")}
      if(data.rows("items").isEmpty()) Text("Add stock items first.")
     }
     if(validation.isNotEmpty()) Text(validation,color=MaterialTheme.colorScheme.error)
     state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
     Spacer(Modifier.height(20.dp))
    }
    Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     TextButton(enabled=!state.busy,onClick={if(canDraft)vm.save(draft,onDismiss)else onDismiss()}){Text(if(canDraft)"Close draft" else "Close")}
     Button(enabled=!state.busy,onClick={
      val missing=form.fields.filter{!it.optional&&values[it.key].isNullOrBlank()}
      validation=when {
       missing.isNotEmpty()->"Complete: "+missing.joinToString{it.label}
       form.lines!=null&&form.lines!="recipe_ids"&&lines.isEmpty()->"Add at least one ingredient line."
       lines.any{it.str("item_id").isBlank()||(it.str("quantity").toBigDecimalOrNull()?.signum()?:0)<=0}->"Each ingredient needs a valid positive quantity."
       else->""
      }
      if(validation.isEmpty()) {if(form.kind=="onboarding"){submitting=true;vm.submit(draft,onDismiss)}else confirm=true}
     }){Text(if(form.kind=="onboarding")"Finish setup" else if(canDraft)"Review & post" else "Continue")}
    }
   }
  }
 }
 if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Confirm ${form.title.lowercase()}?")},text={Text("This action will be sent to the shared workspace. Stock and financial postings require server confirmation.")},confirmButton={TextButton(enabled=!state.busy,onClick={confirm=false;submitting=true;vm.submit(draft,onDismiss)}){Text("Confirm")}},dismissButton={TextButton(onClick={confirm=false}){Text("Review again")}})
}
@Composable fun Choice(label:String,value:String,choices:List<Pair<String,String>>,optional:Boolean=false,onChange:(String)->Unit) {
 var expanded by remember{mutableStateOf(false)}
 Column { Text(label,style=MaterialTheme.typography.labelMedium);OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(choices.find{it.first==value}?.second?:"Choose…")};DropdownMenu(expanded,{expanded=false}){
  if(optional) DropdownMenuItem(text={Text("None")},onClick={onChange("");expanded=false})
  choices.forEach{(key,title)->DropdownMenuItem(text={Text(title)},onClick={onChange(key);expanded=false})}
 } }
}
