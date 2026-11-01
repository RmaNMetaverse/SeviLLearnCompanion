package com.sevil.learncompanion
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import org.junit.Rule
import org.junit.Test
import java.io.File

class NavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        val file=File(compose.activity.getExternalFilesDir(null),name)
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
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
        compose.onNodeWithText("Ask your lecture").performScrollTo().assertIsDisplayed()
        screenshot("ui-chat.png")
    }
}
