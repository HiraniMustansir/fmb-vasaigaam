package org.vasaigaam.fmb

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.vasaigaam.fmb.ui.BohriCalendarPanel
import java.time.LocalDate

/** Tests use isolated UI data and never request a Supabase session or post committee records. */
class BohriCalendarUiTest {
 @get:Rule val compose=createComposeRule()
 private val date=LocalDate.of(2026,2,17)
 private fun show(data:JsonObject=JsonObject(emptyMap()),plan:(JsonObject)->Unit={},source:()->Unit={}) {
  compose.setContent{MaterialTheme{Column(Modifier.verticalScroll(rememberScrollState())){BohriCalendarPanel(data,false,{},plan,source,date)}}}
 }
 @Test fun misriMonthRendersWithGregorianReference(){
  show()
  compose.onNodeWithText("Ramadan al-Muazzam 1447 AH").assertIsDisplayed()
  compose.onNodeWithText("17 Feb 2026 – 18 Mar 2026").assertIsDisplayed()
  val image=compose.onRoot().captureToImage().asAndroidBitmap()
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  File(context.getExternalFilesDir(null),"calendar-preview.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
 }
 @Test fun nextAndPreviousMonthNavigateAcrossCalendarBoundary(){
  show()
  compose.onNodeWithContentDescription("Next month").performClick()
  compose.onNodeWithText("Shawwal al-Mukarram 1447 AH").assertIsDisplayed()
  compose.onNodeWithContentDescription("Previous month").performClick()
  compose.onNodeWithText("Ramadan al-Muazzam 1447 AH").assertIsDisplayed()
 }
 @Test fun gregorianModeUsesGregorianMonth(){
  show()
  compose.onNodeWithText("Gregorian").performClick()
  compose.onNodeWithText("February 2026").assertIsDisplayed()
 }
 @Test fun reviewedCorrectionOverridesCalculatedDay(){
  val data=buildJsonObject{put("calendar_mappings",buildJsonArray{add(buildJsonObject{put("gregorian",date.toString());put("hijri","Reviewed community date");put("source","Committee register");put("verified",true)})})}
  show(data)
  compose.onNodeWithText("Reviewed community date").performScrollTo().assertIsDisplayed()
  compose.onAllNodesWithText("Committee-verified correction",substring=true).onFirst().assertExists()
 }
 @Test fun earlierDatesAreExplicitlyUnavailable(){
  show()
  compose.onNodeWithText("Go to date").performScrollTo().performClick()
  compose.onNode(hasSetTextAction()).performTextReplacement("2024-07-06")
  compose.onNodeWithText("Go",useUnmergedTree=true).performClick()
  compose.onNodeWithText("Hijri calculation unavailable for this date").performScrollTo().assertIsDisplayed()
 }
 @Test fun newMiqaatDefaultsToObservanceAndSelectedDate(){
  var received:JsonObject?=null
  show(plan={received=it})
  compose.onNodeWithText("Add Miqaat or gathering").performScrollTo().performClick()
  compose.runOnIdle{assertEquals("2026-02-17",received?.get("service_date")?.jsonPrimitive?.content);assertEquals("observance",received?.get("kind")?.jsonPrimitive?.content);assertEquals("0",received?.get("planned")?.jsonPrimitive?.content)}
 }
 @Test fun sourceActionOpensOnlyWhenUserClicks(){
  var opened=false
  show(source={opened=true})
  compose.runOnIdle{assertFalse(opened)}
  compose.onNodeWithText("Open source calendar & observances").performScrollTo().performClick()
  compose.runOnIdle{assertTrue(opened)}
 }
}
