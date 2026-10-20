package org.vasaigaam.fmb.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.*
import org.vasaigaam.fmb.BuildConfig
import org.vasaigaam.fmb.domain.PasswordAuthGateway

class PasswordSetupException:IllegalStateException("Account creation is not available yet. Contact the project administrator.")
class SupabasePasswordAuth(private val client:SupabaseClient):PasswordAuthGateway {
 private val settingsClient=HttpClient(OkHttp)
 override suspend fun requirePasswordRegistration() {
  val response=settingsClient.get("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/settings") {
   header("apikey",BuildConfig.SUPABASE_KEY)
  }
  check(response.status.value==200){"Could not check account registration. Please try again."}
  val settings=Json.parseToJsonElement(response.bodyAsText()).jsonObject
  if(settings["mailer_autoconfirm"]?.jsonPrimitive?.booleanOrNull!=true ||
    settings["disable_signup"]?.jsonPrimitive?.booleanOrNull!=false ||
    settings["external"]?.jsonObject?.get("email")?.jsonPrimitive?.booleanOrNull!=true) throw PasswordSetupException()
 }
 override suspend fun register(email:String,password:String) {
  client.auth.signUpWith(Email) { this.email=email;this.password=password }
 }
 override suspend fun signIn(email:String,password:String) {
  client.auth.signInWith(Email) { this.email=email;this.password=password }
 }
 override fun hasSession()=client.auth.currentSessionOrNull()!=null
}
