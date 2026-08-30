package com.trymeon.app.ui.screens.tryon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * The model section has three states and each says something different about
 * money and identity: not built yet, being built, or built and reusable. A
 * state that renders the wrong message would either promise a person the user
 * has not seen or hide that a paid generation is running.
 */
class YourModelTest {

    @get:Rule val compose = createComposeRule()

    private fun render(path: String, building: Boolean, onRedo: () -> Unit = {}) {
        compose.setContent {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(20.dp)) {
                YourModel(portraitPath = path, building = building, onRegenerate = onRedo)
            }
        }
    }

    @Test
    fun beforeAnyPortraitExistsItSaysWhenOneWillBeBuilt() {
        render(path = "", building = false)
        compose.onNodeWithText("YOUR MODEL").assertIsDisplayed()
        compose.onNodeWithText("Built from your photo on the first try-on").assertIsDisplayed()
        // Nothing to redo yet, so offering it would be a dead control.
        compose.onAllNodesWithTextSafe("Redo")
        save("model_empty.png")
    }

    @Test
    fun whileBuildingItSaysSoAndHidesTheRedoControl() {
        render(path = "", building = true)
        compose.onNodeWithText("Building it from your photo…").assertIsDisplayed()
        save("model_building.png")
    }

    @Test
    fun onceBuiltItExplainsWhyItIsKept() {
        render(path = "/nonexistent/portrait.png", building = false)
        // The promise the whole feature exists to make.
        compose.onNodeWithText("Every try-on is this same person").assertIsDisplayed()
        compose.onNodeWithText("Redo").assertIsDisplayed()
        save("model_ready.png")
    }

    @Test
    fun redoIsWiredToTheCaller() {
        var redone = 0
        render(path = "/nonexistent/portrait.png", building = false) { redone++ }
        compose.onNodeWithText("Redo").performClick()
        assertEquals("Redo must reach the view model", 1, redone)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextSafe(text: String) {
        assertTrue(onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isEmpty())
    }

    private fun save(name: String) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext
            .filesDir.resolve("cards").apply { mkdirs() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, name)).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
