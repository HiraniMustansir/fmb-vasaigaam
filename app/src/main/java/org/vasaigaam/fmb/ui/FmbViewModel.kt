package org.vasaigaam.fmb.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.vasaigaam.fmb.data.*
import java.time.Instant

data class AppState(
 val loading:Boolean=true, val signedIn:Boolean=false,
 val data:JsonObject=JsonObject(emptyMap()), val error:String?=null,
 val updated:String="", val busy:Boolean=false, val cached:Boolean=false
)
class FmbViewModel(val repo:Repository):ViewModel() {
 private val mutable=MutableStateFlow(AppState())
 val state=mutable.asStateFlow()
 private var watcher:Job?=null
 private var watched=""
 init {
  run {
   repo.ready()
   mutable.update{it.copy(signedIn=repo.user.isNotEmpty(),loading=false)}
   if(repo.user.isNotEmpty()) {
    repo.cached()?.let { saved->mutable.update{it.copy(data=Json.parseToJsonElement(saved.payload).jsonObject,updated=Instant.ofEpochMilli(saved.saved).toString(),cached=true)} }
    refresh()
   }
  }
  viewModelScope.launch {
   while(isActive) {
    delay(30000)
    if(repo.user.isNotEmpty()) {
     try { refresh() } catch(e:Exception) {
      if(e is CancellationException) throw e
      mutable.update{it.copy(error=message(e),cached=true)}
     }
    } else if(mutable.value.signedIn) {
     repo.clearLocal();mutable.value=AppState(loading=false)
    }
   }
  }
 }
 private fun message(e:Exception):String {
  val detail=e.message.orEmpty()
  // Do not show SDK request headers, bearer tokens or complete response bodies.
  return when {
   "Conflict" in detail->"This record changed. Close the draft, refresh, and review the latest version before editing again."
   "Insufficient" in detail->"There is not enough available stock. Check physical stock and reservations, then retry."
   "Bootstrap" in detail->"The setup code is invalid, expired or already used. Check with the workspace owner."
   "Workspace access" in detail->"Your workspace access is unavailable. Refresh or contact the workspace owner."
   "exceeds" in detail->"The quantity or amount exceeds the remaining balance. Review the original record."
   "Release reservations" in detail->"Release the affected reservations before editing or reconciling this record."
   "Finalized" in detail || "completed or unavailable" in detail->"This service is already finalized or changed. Refresh and review its history."
   "Idempotency" in detail->"This draft was already submitted with different values. Refresh and check its original posting before creating a correction."
   e is IllegalArgumentException->detail.take(150).ifEmpty{"Review the entered values."}
   else->"The request could not be confirmed. Check the values and connection, then retry the saved draft."
  }
 }
 fun dismiss(){mutable.update{it.copy(error=null)}}
 fun run(action:suspend()->Unit){
  viewModelScope.launch {
   mutable.update{it.copy(busy=true,error=null)}
   try { action() } catch(e:Exception) {
    if(e is CancellationException) throw e
    mutable.update{it.copy(error=message(e))}
   } finally { mutable.update{it.copy(busy=false,loading=false)} }
  }
 }
 fun code(email:String,done:()->Unit)=run{repo.requestCode(email.trim());done()}
 fun verify(email:String,code:String)=run{repo.verify(email.trim(),code.trim());mutable.update{it.copy(signedIn=true)};refresh()}
 fun logout()=run{watcher?.cancel();watched="";repo.logout();mutable.value=AppState(loading=false)}
 fun reload()=run{refresh()}
 private suspend fun refresh(){
  val data=repo.snapshot()
  val w=(data["workspace"] as? JsonObject)?.str("id").orEmpty()
  if(w.isEmpty()&&watched.isNotEmpty()){watched="";watcher?.cancel();watcher=null}
  mutable.update{it.copy(data=data,updated=Instant.now().toString(),loading=false,error=null,cached=false)}
  if(w.isNotEmpty()&&watched!=w){
   watched=w;watcher?.cancel()
   watcher=viewModelScope.launch {
    try { repo.watch(w){refresh()} } catch(e:Exception){if(e is CancellationException)throw e /* Polling remains active. */}
   }
  }
 }
 fun submit(draft:Draft,done:()->Unit)=run{
  if(draft.kind !in listOf("bootstrap","join"))repo.save(draft)
  repo.command(draft.kind,Json.parseToJsonElement(draft.payload).jsonObject,draft.id)
  repo.remove(draft.id);done()
  try { refresh() } catch(e:Exception){
   if(e is CancellationException)throw e
   mutable.update{it.copy(error="Posted to the server. The view could not refresh; tap Refresh to load the latest records.",cached=true)}
  }
 }
 fun save(draft:Draft,done:()->Unit)=run{repo.save(draft);done()}
 fun discard(id:String)=run{repo.remove(id)}
}
