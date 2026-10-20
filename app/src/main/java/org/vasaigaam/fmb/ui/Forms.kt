package org.vasaigaam.fmb.ui
import kotlinx.serialization.json.*
import org.vasaigaam.fmb.data.*
import java.time.LocalDate

data class Field(val key:String,val label:String,val default:String="",val options:List<Pair<String,String>> = emptyList(),val optional:Boolean=false)
data class Form(val kind:String,val title:String,val fields:List<Field>,val lines:String?=null,val initial:JsonObject=JsonObject(emptyMap()))
fun form(kind:String,data:JsonObject,initial:JsonObject=JsonObject(emptyMap())):Form {
 fun choices(table:String,label:String="name")=data.rows(table).map { it.str("id") to it.str(label) }
 fun f(k:String,l:String,d:String="",opts:List<Pair<String,String>> = emptyList(),optional:Boolean=false)=Field(k,l,d,opts,optional)
 fun opts(vararg s:String)=s.map { it to it.replaceFirstChar { c->c.uppercase() } }
 val date=LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString()
 val fields=when(kind) {
 "bootstrap"->listOf(f("name","Your name"),f("token","Owner authorization code"))
 "join"->listOf(f("name","Your name"),f("position","Committee position","Committee Member"),f("invitation_id","Invitation",opts=data.rows("invitations").map { it.str("id") to it.str("email") }))
 "settings"->listOf(f("kitchen_name","Kitchen name","FMB Vasaigaam"),f("default_tiffins","Default tiffins","0"),f("people_per_thaal","Default people per thaal","8"))
 "item"->listOf(f("name","Item name"),f("unit","Base unit","kg",opts("kg","g","l","ml","each")),f("minimum","Low stock threshold","0"),f("category","Category","Ingredient"),f("location","Storage location",optional=true))
 "supplier"->listOf(f("name","Supplier name"),f("phone","Phone",optional=true),f("notes","Notes",optional=true))
 "recipe"->listOf(f("name","Recipe name"),f("base_yield","Base yield","100"),f("basis","Yield basis","servings",opts("servings","tiffins","thaals")),f("instructions","Preparation instructions",optional=true),f("allergens","Allergens",optional=true))
 "service"->listOf(f("title","Menu or Miqaat title"),f("service_date","Gregorian date • YYYY-MM-DD",date),f("kind","Service","tiffin",opts("tiffin","gathering","observance")),f("planned","Planned tiffins / thaals",(data["workspace"] as? JsonObject)?.get("settings")?.jsonObject?.str("default_tiffins")?:"0"),f("confirmed","Confirmed tiffins / thaals","0"),f("people_per_thaal","People per thaal","8"),f("buffer","Preparation buffer % (applied once)","0"),f("venue","Venue",optional=true),f("notes","Notes / dietary needs",optional=true),f("budget","Budget ₹","0"))
 "invoice"->listOf(f("supplier_id","Supplier",opts=choices("suppliers")),f("number","Supplier invoice number"),f("invoice_date","Invoice date • YYYY-MM-DD",date))
 "stock"->listOf(f("item_id","Item",opts=choices("items")),f("kind","Movement","opening",opts("opening","receipt","adjustment","return","waste")),f("quantity","Signed quantity in item base unit"),f("unit_cost","Cost ₹ per base unit","0"),f("invoice_line_id","Invoice line (required for receipt)",opts=data.rows("invoice_lines").map { l->l.str("id") to (data.rows("invoices").find { it.str("id")==l.str("invoice_id") }?.str("number").orEmpty()+" • "+data.rows("items").find { it.str("id")==l.str("item_id") }?.str("name")) },optional=true),f("supplier_id","Supplier (for return)",opts=choices("suppliers"),optional=true),f("batch","Batch label",optional=true),f("expiry","Expiry • YYYY-MM-DD",optional=true),f("reason","Reason"))
 "production"->listOf(f("service_id","Service",opts=data.rows("services").filter { it.str("kind")!="observance" && it.str("status") != "cancelled" }.map { it.str("id") to it.str("title") }),f("complete","Complete service after this posting","true",opts("true","false")),f("amendment","Additional posting after completion (requires reason)","false",opts("false","true")),f("prepared","Prepared tiffins / thaals","0"),f("distributed","Distributed / served","0"),f("leftovers","Leftover portions","0"),f("waste","Waste portions","0"),f("redistribution","Redistributed portions","0"),f("notes","Variance / leftover notes",optional=true))
 "reconcile"->listOf(f("item_id","Item",opts=choices("items")),f("counted","Actual counted quantity"),f("reason","Reconciliation reason"))
 "reverse_stock"->listOf(f("id","Movement",opts=data.rows("stock_movements").filter{it.str("kind") !in listOf("consumption","reversal")}.map{it.str("id") to "${it.str("kind")} ${it.str("quantity")} • ${it.str("reason")}"}),f("reason","Correction reason"))
 "payment"->listOf(f("invoice_id","Invoice",opts=choices("invoices","number")),f("amount","Payment amount ₹"),f("account","Paid from","cash",opts("cash","bank")))
 "money"->listOf(f("kind","Record type","expense",opts("expense","advance","settlement","member_expense","reimbursement","contribution","opening")),f("description","Description"),f("amount","Amount ₹"),f("account","Cash / bank","cash",opts("cash","bank")),f("member_id","Member (advance / settlement)",opts=data.rows("memberships").map { it.str("user_id") to it.str("name") },optional=true))
 "invite"->listOf(f("email","Approved member email"))
 "revoke","transfer"->listOf(f("user_id","Member",opts=data.rows("memberships").filter { it.str("active")=="true" }.map { it.str("user_id") to it.str("name") }))
 "profile"->listOf(f("name","Your name"),f("position","Position label"))
 "calendar"->listOf(f("gregorian","Gregorian date • YYYY-MM-DD",date),f("hijri","Bohri Hijri date"),f("source","Source and verification note"),f("verified","Verified","false",opts("false","true")))
 "task"->listOf(f("title","Task"),f("due_date","Due date • YYYY-MM-DD",date),f("assigned_to","Assign to",opts=data.rows("memberships").map { it.str("user_id") to it.str("name") },optional=true))
 "note"->listOf(f("subject","Related menu / Miqaat / bill"),f("body","Note"))
 "reverse_journal"->listOf(f("id","Journal entry",opts=choices("journal_entries","description")),f("reason","Reason for reversal"))
 else->emptyList()
 }
 return Form(kind, when(kind) { "stock"->"Record stock movement"; "money"->"Record money"; "service"->"Plan menu or Miqaat"; "production"->"Confirm cooking"; "bootstrap"->"Set up committee workspace"; "join"->"Accept invitation"; else->kind.replace('_',' ').replaceFirstChar { it.uppercase() } },fields,when(kind){"recipe"->"ingredients";"invoice"->"lines";"production"->"consumption";"service"->"recipe_ids";else->null},initial)
}
