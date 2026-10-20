package org.vasaigaam.fmb

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.koin.android.ext.android.inject
import org.vasaigaam.fmb.data.*
import org.vasaigaam.fmb.ui.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class MainActivity:ComponentActivity() {
 private val repository:Repository by inject()
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setContent {
  val dark=isSystemInDarkTheme()
  MaterialTheme(colorScheme=if(dark) darkColorScheme(primary=Color(0xFF9ED6BF),secondary=Color(0xFFD6BA71)) else lightColorScheme(primary=Color(0xFF145E4D),secondary=Color(0xFF8B6B26),background=Color(0xFFFAF7EF),surface=Color(0xFFFAF7EF))) {
   val vm=viewModel { FmbViewModel(repository) }
   FmbScreen(vm) { content,type->startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { this.type=type; putExtra(Intent.EXTRA_TEXT,content) },"Share committee records")) }
  }
 } }
}
private val money=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
fun JsonObject.num(k:String)=str(k).toBigDecimalOrNull()?:BigDecimal.ZERO
fun fmt(n:BigDecimal)=n.stripTrailingZeros().toPlainString()
fun JsonObject.amount(k:String)=money.format(num(k))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FmbScreen(vm:FmbViewModel,share:(String,String)->Unit) {
 val context=LocalContext.current
 val state by vm.state.collectAsStateWithLifecycle()
 var receiptDialog by remember { mutableStateOf(false) }
 var receiptSubject by rememberSaveable { mutableStateOf("") }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->
  if(uri!=null) vm.run {
   val bytes=withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { stream->
     val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
     while(true){val n=stream.read(buffer);if(n<0)break;require(output.size()+n<=10485760){"Choose a file smaller than 10 MB"};output.write(buffer,0,n)}
     output.toByteArray()
    }?:error("Could not read attachment")
   }
   val ws=(state.data["workspace"] as? JsonObject)?.str("id").orEmpty()
   vm.repo.attach(ws,receiptSubject,bytes,context.contentResolver.getType(uri).orEmpty());vm.reload()
  }
 }
 var tab by rememberSaveable { mutableIntStateOf(0) }
 var more by rememberSaveable { mutableStateOf("Overview") }
 var editing by remember { mutableStateOf<Form?>(null) }
 var draftId by remember { mutableStateOf<String?>(null) }
 val data=state.data
 val workspace=data["workspace"] as? JsonObject
 val w=workspace?.str("id").orEmpty()
 val drafts by remember(w) { vm.repo.drafts(w) }.collectAsStateWithLifecycle(emptyList())
 fun open(kind:String,initial:JsonObject=JsonObject(emptyMap())) { draftId=null; editing=form(kind,data,initial) }
 LaunchedEffect(state.signedIn,data.str("needs_onboarding")) {
  if(state.signedIn&&data.str("needs_onboarding")=="true"&&editing==null) {draftId=null;editing=form("onboarding",data)}
 }
 val owner=workspace?.str("owner_id")==vm.repo.user
 Scaffold(topBar={ TopAppBar(title={ Column { Text("FMB Vasaigaam",fontWeight=FontWeight.Bold); Text("Committee kitchen workspace",style=MaterialTheme.typography.labelMedium) } },actions={ if(state.signedIn) { IconButton(onClick={vm.reload()}){Icon(Icons.Outlined.Refresh,"Refresh")}; IconButton(onClick={vm.logout()}){Icon(Icons.Outlined.Logout,"Sign out")} } }) },bottomBar={ if(w.isNotEmpty()) NavigationBar {
  listOf("Home" to Icons.Outlined.Home,"Menu" to Icons.Outlined.RestaurantMenu,"Stock" to Icons.Outlined.Inventory2,"Miqaats" to Icons.Outlined.CalendarMonth,"Accounts" to Icons.Outlined.AccountBalanceWallet).forEachIndexed { i,p->NavigationBarItem(selected=tab==i,onClick={tab=i;more="Overview"},icon={Icon(p.second,p.first)},label={Text(p.first)}) }
 } }) { pad->
 Column(Modifier.fillMaxSize().padding(pad)) {
  if(state.busy||state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
  state.error?.let { error->Card(Modifier.fillMaxWidth().padding(12.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(16.dp)) { Text(error); TextButton(onClick={vm.dismiss()}){Text("Dismiss")} } } }
  if(!state.signedIn) Login(vm,state.busy)
  else if(w.isEmpty()) Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   Image(painterResource(R.drawable.jamaat_logo),"Anjuman E Badri Vasai Gaam logo",Modifier.size(144.dp).align(Alignment.CenterHorizontally))
   Text("Welcome to your committee workspace",style=MaterialTheme.typography.headlineSmall)
   if(data.str("access_revoked")=="true") {
    Text("Your workspace access has been revoked. Contact the project administrator.")
   } else {
    Text("One last step: enter your name and committee position to join the kitchen workspace.")
    Button(onClick={open("onboarding")},enabled=!state.busy&&!state.loading&&data.str("needs_onboarding")=="true") {Text("Complete profile")}
   }
  }
  else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   item { Text(if(state.error==null&&!state.cached) "Updated ${state.updated.substringAfter('T').take(5)} UTC • server records" else "Connection issue • last saved view ${state.updated}",style=MaterialTheme.typography.labelSmall) }
   item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    val tools=when(tab){0->listOf("Overview","Tasks","Activity","Drafts","Members");1->listOf("Overview","Recipes","Cooking","Notes");2->listOf("Overview","Shopping","Suppliers","Purchases","Movements");3->listOf("Overview","Calendar","History");else->listOf("Overview","Journal","Balances","Receipts")}
    tools.forEach { FilterChip(selected=more==it,onClick={more=it},label={Text(it)}) }
   } }
   when {
    more=="Drafts"->{
     item { Heading("Offline drafts","Saved on this device. Drafts are not posted transactions.") }
     if(drafts.isEmpty()) item { Empty("No saved drafts") }
     drafts.forEach { d->item { Record(d.kind,"Saved locally • ${d.saved}") { TextButton(onClick={draftId=d.id;editing=form(d.kind,data,Json.parseToJsonElement(d.payload).jsonObject)}){Text("Review & post")}; TextButton(onClick={vm.discard(d.id)}){Text("Discard")} } } }
    }
    more=="Members"->{
     item { Heading("Committee","All active members can edit operational records. Position labels do not grant permissions.") }
     item { Actions(listOf("My profile" to {open("profile")})+if(owner) listOf("Revoke access" to {open("revoke")},"Transfer ownership" to {open("transfer")},"Kitchen settings" to {open("settings",workspace?.get("settings")?.jsonObject?:JsonObject(emptyMap()))}) else emptyList()) }
     data.rows("memberships").forEach { r->item { Record(r.str("name"),r.str("position")+if(r.str("active")=="true") " • Active" else " • Revoked") } }
    }
    more=="Tasks"->{
     item { Heading("Committee tasks","Assignments and due dates"); Actions(listOf("Add task" to {open("task")})) }
     data.rows("tasks").sortedBy{it.str("due_date")}.forEach { r->item { Record(r.str("title"),"Due ${r.str("due_date")} • "+if(r.str("done")=="true") "Completed" else "Open") { TextButton(onClick={val payload=buildJsonObject{put("workspace_id",w);put("id",r.str("id"));put("version",r["version"]!!);put("done",r.str("done")!="true")};vm.submit(Draft(user=vm.repo.user,workspace=w,kind="task",payload=payload.toString())){}}){Text(if(r.str("done")=="true")"Reopen" else "Complete")} } } }
    }
    more=="Activity"->{ item { Heading("Shared activity","Recent changes recorded by the server") }; data.rows("audit_events").forEach { r->item{Record(r.str("kind").replace('_',' '),r.str("created_at")+" • "+(data.rows("memberships").find{it.str("user_id")==r.str("actor")}?.str("name")?:"Member"))} } }
    more=="Recipes"->{
     item { Heading("Recipe library","Quantities use each item's base unit. Production preserves the recipe version.");Actions(listOf("Add recipe" to {open("recipe")})) }
     data.rows("recipes").forEach { r->item{Record(r.str("name"),"Base ${r.str("base_yield")} ${r.str("basis")} • version ${r.str("version")}\n${r.str("allergens")}"){TextButton(onClick={open("recipe",buildJsonObject{r.forEach{(k,v)->put(k,v)};put("ingredients",JsonArray(data.rows("recipe_ingredients").filter{it.str("recipe_id")==r.str("id")}))})}){Text("Edit recipe")}}} }
    }
    more=="Cooking"->{ item { Heading("Kitchen production","Confirm actual ingredient quantities. Posting consumes stock once and completes the service.");Actions(listOf("Record cooking" to {open("production")})) }; data.rows("services").filter{it.str("kind")!="observance"&&it.str("status") !in listOf("completed","cancelled")}.forEach { r->item{ServiceCard(r,data,vm,w,{open("service",it)},share)} } }
    more=="Notes"->{ item {Actions(listOf("Add note" to {open("note")}))};data.rows("notes").forEach{r->item{Record(r.str("subject"),r.str("body"))}} }
    more=="Suppliers"->{ item{Heading("Suppliers","Shared supplier directory");Actions(listOf("Add supplier" to {open("supplier")}))};data.rows("suppliers").forEach{r->item{Record(r.str("name"),r.str("phone")+"\n"+r.str("notes"))}} }
    more=="Purchases"->{ item{Heading("Supplier bills","A bill records a liability. Receive goods and record payments separately.");Actions(listOf("Add bill" to {open("invoice")},"Receive goods" to {open("stock")}))};data.rows("invoices").forEach{r->item{Record("Bill ${r.str("number")}","${r.str("invoice_date")} • ${r.amount("total")}\n"+data.rows("suppliers").find{it.str("id")==r.str("supplier_id")}?.str("name"))}} }
    more=="Movements"->{ item{Heading("Stock movement ledger","Append-only quantities and costs")};data.rows("stock_movements").sortedByDescending{it.str("created_at")}.forEach{r->item{Record(data.rows("items").find{it.str("id")==r.str("item_id")}?.str("name").orEmpty(),"${r.str("kind")} • ${r.str("quantity")}\n${r.str("reason")} • ${r.str("created_at")}")}} }
    more=="Shopping"->{ item{Heading("Combined shopping list","All active menus and gatherings; each ingredient is counted once. Amounts use base units.")};data.rows("items").forEach { r->
     val needed=data.rows("demand").filter{it.str("item_id")==r.str("id")}.fold(BigDecimal.ZERO){a,b->a+b.num("required")}
     val held=data.rows("stock_movements").filter{it.str("item_id")==r.str("id")}.fold(BigDecimal.ZERO){a,b->a+b.num("quantity")}
     if(needed>BigDecimal.ZERO) item{Record(r.str("name"),"Required ${fmt(needed)} • On hand ${fmt(held)}\nShortage ${fmt((needed-held).max(BigDecimal.ZERO))} ${r.str("unit")}")}
    } }
    more=="Calendar"->{
     item{Heading("Bohri calendar","Only maintained mappings are shown. Unmapped dates are unverified. Dates use Asia/Kolkata, midnight boundaries; no sunset conversion.");if(owner) Actions(listOf("Add verified date mapping" to {open("calendar")}))}
     if(data.rows("calendar_mappings").isEmpty()) item{Empty("No verified date range is available yet.")}
     data.rows("calendar_mappings").sortedBy{it.str("gregorian")}.forEach{r->item{Record("${r.str("gregorian")} • ${r.str("hijri")}","${if(r.str("verified")=="true")"Verified" else "Unverified"} • ${r.str("source")}"){if(owner)TextButton(onClick={open("calendar",r)}){Text("Edit mapping")}}}}
    }
    more=="History"->{ item{Heading("Production history","Outputs, leftovers and waste")}; data.rows("production_runs").forEach{r->item{Record(data.rows("services").find{it.str("id")==r.str("service_id")}?.str("title").orEmpty(),"Prepared ${r.str("prepared")} • Served ${r.str("distributed")}\nLeftovers ${r.str("leftovers")} • Waste ${r.str("waste")}\n${r.str("notes")}")}} }
    more=="Journal"->{ item{Actions(listOf("Reverse record" to {open("reverse_journal")},"Share CSV" to {share(journalCsv(data),"text/csv")}))};data.rows("journal_entries").sortedByDescending{it.str("created_at")}.forEach{r->item{Record(r.str("description"),"${r.str("kind")} • ${r.str("created_at")}\n"+data.rows("journal_lines").filter{it.str("entry_id")==r.str("id")}.joinToString("\n"){"${it.str("account_code")}: Dr ${it.amount("debit")} / Cr ${it.amount("credit")}"})}} }
    more=="Receipts"->{item{Heading("Receipts & evidence","Private JPEG, PNG and PDF files. Attach evidence after reviewing the source; uploads never post stock or money.");Actions(listOf("Upload receipt" to {receiptSubject="";receiptDialog=true}))};data.rows("attachments").forEach{r->item{Record(r.str("subject"),r.str("path").substringAfter('/')){TextButton(onClick={vm.run{val url=vm.repo.attachmentUrl(r.str("path"));context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}){Text("Open attachment")}}}}}
    more=="Balances"->{item{Heading("Account balances","Debit less credit. Negative payable balances represent money owed.")};data.rows("accounts").forEach{r->val balance=data.rows("journal_lines").filter{it.str("account_code")==r.str("code")}.fold(BigDecimal.ZERO){a,b->a+b.num("debit")-b.num("credit")};item{Record(r.str("name"),money.format(balance))}}}
    tab==0->{
     item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) { Image(painterResource(R.drawable.jamaat_logo),"Jamaat logo",Modifier.size(76.dp));Column{Text("Khidmat, together",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(LocalDate.now(ZoneId.of("Asia/Kolkata")).toString());Text(data.rows("calendar_mappings").find{it.str("gregorian")==LocalDate.now(ZoneId.of("Asia/Kolkata")).toString()}?.str("hijri")?:"Bohri date unverified",style=MaterialTheme.typography.labelMedium)} } }
     item { Actions(listOf("Plan menu" to {open("service")},"Record stock" to {open("stock")},"Add bill" to {open("invoice")},"Add expense" to {open("money")})) }
     item {
      val incomplete=listOf("Ingredients" to data.rows("items").isNotEmpty(),"Suppliers" to data.rows("suppliers").isNotEmpty(),"Recipes" to data.rows("recipes").isNotEmpty(),"Opening stock" to data.rows("stock_movements").isNotEmpty(),"Opening balances" to data.rows("journal_entries").any{it.str("kind")=="opening"},"Tiffin count" to (((workspace?.get("settings") as? JsonObject)?.num("default_tiffins")?:BigDecimal.ZERO)>BigDecimal.ZERO)).filter{!it.second}.map{it.first}
      if(incomplete.isNotEmpty()) Record("Kitchen setup remaining",incomplete.joinToString(" • "))
     }
     item{Heading("Needs attention","${data.rows("tasks").count{it.str("done")!="true"}} open tasks • ${drafts.size} local drafts")}
     val services=data.rows("services").filter{it.str("status") !in listOf("cancelled","completed")}.sortedBy{it.str("service_date")}
     if(services.isEmpty()) item{Empty("Your kitchen plan starts here. Add ingredients, a recipe and your first menu.")}
     services.take(5).forEach{r->item{ServiceCard(r,data,vm,w,{open("service",it)},share)}}
     item{Heading("Stock alerts", "Items at or below their minimum stock")}
     data.rows("items").forEach{r->val onHand=data.rows("stock_movements").filter{it.str("item_id")==r.str("id")}.fold(BigDecimal.ZERO){a,b->a+b.num("quantity")};if(onHand<=r.num("minimum")) item{Record(r.str("name"),"${fmt(onHand)} ${r.str("unit")} • minimum ${r.str("minimum")}")}}
    }
    tab==1||tab==3->{
     item{Heading(if(tab==1)"Weekly kitchen plan" else "Miqaats & gatherings",if(tab==1)"Publishing does not deduct stock. Confirm cooking to record actual consumption." else "Observances do not create cooking demand. A gathering's counts are thaals.");Actions(listOf("Plan menu or Miqaat" to {open("service")}))}
     val rows=data.rows("services").filter{if(tab==1)it.str("kind")=="tiffin" else it.str("kind")!="tiffin"}.sortedBy{it.str("service_date")}
     if(rows.isEmpty()) item{Empty("No plans yet. Create your first plan.")}
     rows.forEach{r->item{ServiceCard(r,data,vm,w,{open("service",it)},share)}}
    }
    tab==2->{
     item{Heading("Kitchen stock","Physical stock comes from the movement ledger. Reservations keep planned cooking separate.");Actions(listOf("Add item" to {open("item")},"Record movement" to {open("stock")},"Stock count" to {open("reconcile")},"Reverse movement" to {open("reverse_stock")}))}
     if(data.rows("items").isEmpty()) item{Empty("Add your ingredients and opening stock to begin.")}
     data.rows("items").sortedBy{it.str("name")}.forEach{r->val held=data.rows("stock_movements").filter{it.str("item_id")==r.str("id")}.fold(BigDecimal.ZERO){a,b->a+b.num("quantity")};val reserved=data.rows("reservations").filter{it.str("item_id")==r.str("id")}.fold(BigDecimal.ZERO){a,b->a+b.num("quantity")};item{Record(r.str("name"),"On hand ${fmt(held)} ${r.str("unit")} • Reserved ${fmt(reserved)}\nAvailable ${fmt(held-reserved)} • Minimum ${r.str("minimum")}")}}
    }
    tab==4->{
     item{Heading("Accounts","INR • Bills, inventory value and cash movement are recorded separately.");Actions(listOf("Record money" to {open("money")},"Pay supplier" to {open("payment")},"Add supplier bill" to {open("invoice")}))}
     data.rows("invoices").forEach{r->val entryIds=data.rows("journal_entries").filter{it.str("invoice_id")==r.str("id")}.map{it.str("id")};val outstanding=data.rows("journal_lines").filter{it.str("entry_id") in entryIds&&it.str("account_code")=="payable"}.fold(BigDecimal.ZERO){a,b->a+b.num("credit")-b.num("debit")};item{Record("Bill ${r.str("number")}","Total ${r.amount("total")} • Outstanding ${money.format(outstanding)}")}}
     if(data.rows("invoices").isEmpty()) item{Empty("No supplier bills recorded.")}
    }
   }
   item{Spacer(Modifier.height(24.dp))}
  }
 }
 }
 if(receiptDialog) AlertDialog(onDismissRequest={receiptDialog=false},title={Text("Receipt reference")},text={OutlinedTextField(receiptSubject,{receiptSubject=it},label={Text("Bill number / expense description")})},confirmButton={TextButton(onClick={receiptDialog=false;picker.launch(arrayOf("image/jpeg","image/png","application/pdf"))},enabled=receiptSubject.isNotBlank()){Text("Choose file")}},dismissButton={TextButton(onClick={receiptDialog=false}){Text("Cancel")}})
 editing?.let { f-> FormDialog(f,data,vm,draftId,onDismiss={editing=null}) }
}

@Composable fun Login(vm:FmbViewModel,busy:Boolean) {
 var email by rememberSaveable{mutableStateOf("")}
 var registering by rememberSaveable{mutableStateOf(false)}
 // Passwords deliberately do not use rememberSaveable or the offline draft database.
 var password by remember{mutableStateOf("")}
 var confirmation by remember{mutableStateOf("")}
 var visible by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  Image(painterResource(R.drawable.jamaat_logo),"Jamaat logo",Modifier.size(128.dp).align(Alignment.CenterHorizontally))
  Text(if(registering)"Create your account" else "Welcome to FMB Vasaigaam",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
  Text("Sign in with your email and password. New members can create an account and complete their committee profile.")
  OutlinedTextField(email,{email=it},label={Text("Email address")},enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email,imeAction=ImeAction.Next),modifier=Modifier.fillMaxWidth(),singleLine=true)
  OutlinedTextField(password,{password=it},label={Text("Password")},enabled=!busy,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=if(registering)ImeAction.Next else ImeAction.Done),supportingText=if(registering)({Text("At least 8 characters")})else null,trailingIcon={IconButton(onClick={visible=!visible}){Icon(if(visible)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(visible)"Hide password" else "Show password")}},modifier=Modifier.fillMaxWidth(),singleLine=true)
  if(registering)OutlinedTextField(confirmation,{confirmation=it},label={Text("Confirm password")},enabled=!busy,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=ImeAction.Done),modifier=Modifier.fillMaxWidth(),singleLine=true)
  Button(onClick={vm.authenticate(email,password,if(registering)confirmation else null){password="";confirmation=""}},enabled=!busy&&email.isNotBlank()&&password.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {Text(if(registering)"Create account" else "Sign in")}
  TextButton(onClick={registering=!registering;password="";confirmation="";visible=false;vm.dismiss()},enabled=!busy){Text(if(registering)"Already have an account? Sign in" else "New here? Create an account")}
  Text("Forgot your password or previously signed in by email code? Contact the project administrator to set a password.",style=MaterialTheme.typography.bodySmall)
 }
}
@Composable fun Heading(title:String,description:String) { Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(4.dp));Text(description,style=MaterialTheme.typography.bodyMedium) }
@Composable fun Empty(text:String) { Card(Modifier.fillMaxWidth()){Text(text,Modifier.padding(24.dp))} }
@Composable fun Record(title:String,detail:String,actions:(@Composable RowScope.()->Unit)?=null) { Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(detail,style=MaterialTheme.typography.bodyMedium);actions?.let{Row(Modifier.horizontalScroll(rememberScrollState()),content=it)}}} }
@Composable fun Actions(actions:List<Pair<String,()->Unit>>) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){actions.forEach{(label,action)->OutlinedButton(onClick=action){Text(label)}}} }
@Composable fun ServiceCard(r:JsonObject,data:JsonObject,vm:FmbViewModel,w:String,edit:(JsonObject)->Unit,share:(String,String)->Unit) {
 val kind=r.str("kind");val people=r.num("planned")*r.num("people_per_thaal")
 val recipes=data.rows("service_dishes").filter{it.str("service_id")==r.str("id")}.map{d->data.rows("recipes").find{it.str("id")==d.str("recipe_id")}?.str("name").orEmpty()}
 Record(r.str("title"),"${r.str("service_date")} • ${r.str("status")}\n${recipes.joinToString(" • ")}\nPlanned ${r.str("planned")} ${if(kind=="gathering")"thaals (${fmt(people)} people)" else if(kind=="observance")"• observance only" else "tiffins"}\nConfirmed ${r.str("confirmed")} • Prepared ${r.str("prepared")} • Distributed ${r.str("distributed")}\nBuffer ${r.str("buffer")}%") {
  if(r.str("status") !in listOf("completed","cancelled")) {
   TextButton(onClick={
    val initial=buildJsonObject {
     r.forEach { (k,v)->put(k,v) }
     put("recipe_ids",JsonArray(data.rows("service_dishes").filter{it.str("service_id")==r.str("id")}.map{JsonPrimitive(it.str("recipe_id"))}))
    }
    edit(initial)
   }) { Text("Edit") }
   TextButton(onClick={val payload=buildJsonObject{put("workspace_id",w);put("id",r.str("id"));put("version",r["version"]!!);put("status","published")};vm.submit(Draft(user=vm.repo.user,workspace=w,kind="status",payload=payload.toString())){}}){Text("Publish")}
   if(kind!="observance") TextButton(onClick={val payload=buildJsonObject{put("workspace_id",w);put("id",r.str("id"))};vm.submit(Draft(user=vm.repo.user,workspace=w,kind="reserve",payload=payload.toString())){}}){Text("Reserve")}
   if(data.rows("reservations").any{it.str("service_id")==r.str("id")}) TextButton(onClick={val payload=buildJsonObject{put("workspace_id",w);put("id",r.str("id"));put("version",r["version"]!!);put("status","draft")};vm.submit(Draft(user=vm.repo.user,workspace=w,kind="status",payload=payload.toString())){}}){Text("Release reservations")}
   TextButton(onClick={val payload=buildJsonObject{put("workspace_id",w);put("id",r.str("id"));put("version",r["version"]!!);put("status","cancelled")};vm.submit(Draft(user=vm.repo.user,workspace=w,kind="status",payload=payload.toString())){}}){Text("Cancel")}
  }
  TextButton(onClick={edit(buildJsonObject {
   r.filterKeys{it !in listOf("id","version","status","prepared","distributed")}.forEach{(k,v)->put(k,v)}
   put("recipe_ids",JsonArray(data.rows("service_dishes").filter{it.str("service_id")==r.str("id")}.map{JsonPrimitive(it.str("recipe_id"))}))
  })}){Text("Copy")}
  TextButton(onClick={share("FMB Vasaigaam\n${r.str("service_date")} • ${r.str("title")}\n${recipes.joinToString("\n")}\n${r.str("notes")}","text/plain")}){Text("Share")}
 }
}
fun journalCsv(data:JsonObject):String {
 fun cell(s:String)="\""+s.replace("\"","\"\"")+"\""
 return "entry,date,description,account,debit,credit\n"+data.rows("journal_lines").joinToString("\n"){l->val e=data.rows("journal_entries").find{it.str("id")==l.str("entry_id")};listOf(l.str("entry_id"),e?.str("created_at").orEmpty(),e?.str("description").orEmpty(),l.str("account_code"),l.str("debit"),l.str("credit")).joinToString(","){cell(it)}}
}
