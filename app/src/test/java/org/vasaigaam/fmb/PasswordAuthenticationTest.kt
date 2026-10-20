package org.vasaigaam.fmb

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.vasaigaam.fmb.domain.PasswordAuthentication
import org.vasaigaam.fmb.domain.PasswordAuthGateway

class PasswordAuthenticationTest {
 private class Gateway:PasswordAuthGateway {
  var allowed=true;var establishesSession=true;var session=false
  var registrations=0;var logins=0;var address="";var secret=""
  override suspend fun requirePasswordRegistration(){check(allowed){"Confirmation still enabled"}}
  override suspend fun register(email:String,password:String){registrations++;address=email;secret=password;session=establishesSession}
  override suspend fun signIn(email:String,password:String){logins++;address=email;secret=password;session=establishesSession}
  override fun hasSession()=session
 }
 @Test fun newAccountCreatesSessionAndPreservesPasswordWhitespace()=runBlocking {
  val g=Gateway();PasswordAuthentication(g).register(" Member@Example.com "," pass word "," pass word ")
  assertEquals("member@example.com",g.address);assertEquals(" pass word ",g.secret);assertTrue(g.session)
 }
 @Test fun existingAccountCanSignInWithoutRegistering()=runBlocking {
  val g=Gateway();PasswordAuthentication(g).signIn("member@example.com","old123")
  assertEquals(1,g.logins);assertEquals(0,g.registrations);assertTrue(g.session)
 }
 @Test fun confirmationRequiredConfigurationStopsBeforeSignupCanSendEmail()=runBlocking {
  val g=Gateway().apply{allowed=false}
  assertThrows(IllegalStateException::class.java){runBlocking{PasswordAuthentication(g).register("a@example.com","password123","password123")}}
  assertEquals(0,g.registrations);assertFalse(g.session)
 }
 @Test fun noSessionAfterSignupIsNotReportedAsSignedIn()=runBlocking {
  val g=Gateway().apply{establishesSession=false}
  assertThrows(IllegalStateException::class.java){runBlocking{PasswordAuthentication(g).register("a@example.com","password123","password123")}}
  assertFalse(g.session)
 }
 @Test fun mismatchedPasswordsNeverReachServer()=runBlocking {
  val g=Gateway()
  assertThrows(IllegalArgumentException::class.java){runBlocking{PasswordAuthentication(g).register("a@example.com","password123","different123")}}
  assertEquals(0,g.registrations)
 }
 @Test fun invalidAddressAndBlankLoginNeverReachServer()=runBlocking {
  val g=Gateway()
  assertThrows(IllegalArgumentException::class.java){runBlocking{PasswordAuthentication(g).signIn("invalid","password123")}}
  assertThrows(IllegalArgumentException::class.java){runBlocking{PasswordAuthentication(g).signIn("a@example.com","")}}
  assertEquals(0,g.logins)
 }
 @Test fun shortNewPasswordNeverReachesServer()=runBlocking {
  val g=Gateway()
  assertThrows(IllegalArgumentException::class.java){runBlocking{PasswordAuthentication(g).register("a@example.com","short","short")}}
  assertEquals(0,g.registrations)
 }
}
