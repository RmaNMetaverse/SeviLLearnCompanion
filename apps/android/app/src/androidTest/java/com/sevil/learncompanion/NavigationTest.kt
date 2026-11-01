package com.sevil.learncompanion
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        // MediaStore keeps the preview when Gradle uninstalls the tested app.
        val resolver=compose.activity.contentResolver
        val values=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name)
            put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/sevil-ui")
        }
        val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error("Could not retain UI preview")
        resolver.openOutputStream(uri)!!.use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun phoneNavigationAndClassroomChatAreReachable() {
        compose.waitForIdle();screenshot("ui-home.png")
        compose.onNodeWithTag("nav-courses").performClick()
        compose.onNodeWithText("Search your library").assertIsDisplayed()
        compose.onNodeWithTag("nav-sessions").performClick()
        compose.onNodeWithText("Search your library").assertIsDisplayed()
        compose.onNodeWithTag("nav-overview").performClick()
        compose.onNodeWithText("Try a sample lecture").performScrollTo().performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithText("Chat").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Chat").performScrollTo().performClick()
        compose.onNodeWithTag("session-tutor-panel").performScrollTo()
        compose.onNodeWithText("Ask your lecture").performScrollTo()
        screenshot("ui-chat.png")
        compose.onNodeWithText("Ask your lecture").assertIsDisplayed()
    }
}
