package org.vasaigaam.fmb

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.vasaigaam.fmb.ui.*
import java.time.LocalDate

/** Disposable QA package: no login, network posts or real committee records. */
class WorkspaceUiTest {
 @get:Rule val compose=createComposeRule()
 @Test fun weekNavigationAndReset(){
  var selected:LocalDate?=null
  compose.setContent{FmbTheme{WeekPicker(LocalDate.of(2026,10,19)){selected=it}}}
  compose.onNodeWithContentDescription("Next week").performClick()
  compose.runOnIdle{assertEquals(LocalDate.of(2026,10,26),selected)}
  compose.onNodeWithContentDescription("Previous week").performClick()
  compose.runOnIdle{assertEquals(LocalDate.of(2026,10,12),selected)}
  compose.onNodeWithText("This week").performClick()
  compose.runOnIdle{assertNotNull(selected)}
 }
 @Test fun reportRejectsInvalidRangeWithoutCrashing(){
  compose.setContent{FmbTheme{Column(Modifier.verticalScroll(rememberScrollState())){FinancialReport(JsonObject(emptyMap()))}}}
  compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("not a date")
  compose.onNodeWithText("Enter valid dates with the end on or after the start.").performScrollTo().assertIsDisplayed()
 }
 @Test fun reportsRenderInDarkTheme(){
  compose.setContent{FmbTheme(dark=true){Surface{Column(Modifier.verticalScroll(rememberScrollState())){FinancialReport(JsonObject(emptyMap()))}}}}
  compose.onNodeWithText("Contributions").assertExists()
  val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  File(context.getExternalFilesDir(null),"reports-dark.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
  compose.onNodeWithText("Costs appear after actual cooking has been recorded.").performScrollTo().assertIsDisplayed()
 }
 @Test fun dashboardShortcutsReachTasksAndDrafts(){
  var tasks=false;var drafts=false
  compose.setContent{FmbTheme{WorkspaceSummary(JsonObject(emptyMap()),2,{tasks=true},{drafts=true})}}
  compose.onNodeWithText("Open tasks").performClick()
  compose.onNodeWithText("Drafts").performClick()
  compose.runOnIdle{assertTrue(tasks);assertTrue(drafts)}
 }
}
