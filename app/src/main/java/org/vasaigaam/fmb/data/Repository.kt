package org.vasaigaam.fmb.data

import androidx.room.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.*
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.time.Duration.Companion.seconds
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import org.vasaigaam.fmb.BuildConfig
import java.util.UUID

@Entity(tableName="drafts")
data class Draft(@androidx.room.PrimaryKey val id: String = UUID.randomUUID().toString(), val user: String, val workspace: String, val kind: String, val payload: String, val saved: Long = System.currentTimeMillis())
@Dao interface DraftDao {
 @Query("SELECT * FROM drafts WHERE user=:user AND workspace=:workspace ORDER BY saved DESC") fun observe(user: String, workspace: String): Flow<List<Draft>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(draft: Draft)
 @Query("DELETE FROM drafts WHERE id=:id") suspend fun remove(id: String)
 @Query("DELETE FROM drafts") suspend fun clear()
}
@Entity(tableName="snapshots")
data class CachedSnapshot(@androidx.room.PrimaryKey val user: String, val payload: String, val saved: Long=System.currentTimeMillis())
@Dao interface SnapshotDao {
 @Query("SELECT * FROM snapshots WHERE user=:user LIMIT 1") suspend fun get(user:String): CachedSnapshot?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(snapshot:CachedSnapshot)
 @Query("DELETE FROM snapshots") suspend fun clear()
}
@Database(entities=[Draft::class,CachedSnapshot::class], version=1, exportSchema=false)
abstract class DraftDatabase : RoomDatabase() { abstract fun drafts(): DraftDao; abstract fun snapshots(): SnapshotDao }
class Repository(private val db: DraftDatabase) {
 val client = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) { install(Auth); install(Postgrest); install(Realtime); install(Storage) }
 val user get() = client.auth.currentUserOrNull()?.id.orEmpty()
 suspend fun ready() { client.auth.awaitInitialization() }
 suspend fun requestCode(email: String) { client.auth.signInWith(OTP) { this.email=email } }
 suspend fun verify(email: String, code: String) { client.auth.verifyEmailOtp(type=OtpType.Email.EMAIL, email=email, token=code) }
 suspend fun logout() { try { client.auth.signOut() } catch(e:Exception) { if(e is kotlinx.coroutines.CancellationException) throw e } finally { client.auth.clearSession(); clearLocal() } }
 suspend fun clearLocal() { db.drafts().clear(); db.snapshots().clear() }
 suspend fun cached():CachedSnapshot? = db.snapshots().get(user)?.takeIf { System.currentTimeMillis()-it.saved<24*60*60*1000L }
 suspend fun snapshot(): JsonObject {
  val data=client.postgrest.rpc("workspace_snapshot").decodeAs<JsonObject>()
  if(data["workspace"] is JsonObject) db.snapshots().save(CachedSnapshot(user,data.toString())) else clearLocal()
  return data
 }
 suspend fun command(kind: String, data: JsonObject, key: String = UUID.randomUUID().toString()): JsonElement = client.postgrest.rpc("execute_command", buildJsonObject {
  put("p_kind",kind); put("p_data",data); put("p_key",key)
 }).decodeAs<JsonElement>()
 suspend fun attach(workspace:String,subject:String,bytes:ByteArray,mime:String) {
  require(bytes.size in 1..10485760) { "Attachment must be 10 MB or smaller" }
  require(mime in listOf("image/jpeg","image/png","application/pdf")) { "Choose a JPEG, PNG or PDF" }
  val id=UUID.randomUUID().toString()
  val path="$workspace/$id.${when(mime){"image/jpeg"->"jpg";"image/png"->"png";else->"pdf"}}"
  client.storage.from("committee-attachments").upload(path,bytes) { upsert=false;contentType=ContentType.parse(mime) }
  command("attachment",buildJsonObject{put("workspace_id",workspace);put("path",path);put("subject",subject)},id)
 }
 suspend fun attachmentUrl(path:String)=client.storage.from("committee-attachments").createSignedUrl(path,60.seconds)
 fun drafts(workspace: String) = db.drafts().observe(user, workspace)
 suspend fun save(draft: Draft) = db.drafts().save(draft)
 suspend fun remove(id: String) = db.drafts().remove(id)
 suspend fun watch(workspace: String, refresh: suspend () -> Unit) {
  val channel = client.channel("workspace-$workspace")
  try {
   val changes = channel.postgresChangeFlow<PostgresAction>(schema="public") { table="audit_events" }
   channel.subscribe()
   changes.collect { refresh() }
  } finally { client.realtime.removeChannel(channel) }
 }
}
fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
fun JsonObject.rows(key: String) = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
