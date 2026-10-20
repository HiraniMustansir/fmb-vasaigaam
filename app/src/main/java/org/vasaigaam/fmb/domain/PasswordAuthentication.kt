package org.vasaigaam.fmb.domain

/** Passwords stay in the call stack/UI memory; neither drafts nor snapshots receive them. */
interface PasswordAuthGateway {
 suspend fun requirePasswordRegistration()
 suspend fun register(email:String,password:String)
 suspend fun signIn(email:String,password:String)
 fun hasSession():Boolean
}
class PasswordAuthentication(private val gateway:PasswordAuthGateway) {
 private fun normalizeEmail(value:String):String {
  val normalized=value.trim().lowercase(java.util.Locale.ROOT)
  require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(normalized)){"Enter a valid email address."}
  return normalized
 }
 suspend fun signIn(email:String,password:String) {
  val address=normalizeEmail(email)
  require(password.isNotEmpty()){"Enter your password."}
  gateway.signIn(address,password)
  check(gateway.hasSession()){"Sign-in did not create a session. Please try again."}
 }
 suspend fun register(email:String,password:String,confirmation:String) {
  val address=normalizeEmail(email)
  require(password.length>=8){"Choose a password with at least 8 characters."}
  require(password==confirmation){"The passwords do not match."}
  // Check server configuration before signup so this flow never intentionally requests a confirmation email.
  gateway.requirePasswordRegistration()
  gateway.register(address,password)
  check(gateway.hasSession()){"Account registration did not create a session. Contact the project administrator."}
 }
}
