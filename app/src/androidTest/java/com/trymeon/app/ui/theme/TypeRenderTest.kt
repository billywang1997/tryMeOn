package com.trymeon.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test

/**
 * A specimen of the type scale, rendered by Compose rather than read off a
 * device screenshot.
 *
 * The closet header looked as though its capitals were being cropped, and a
 * screenshot cannot tell a layout fault from the emulator's own rasteriser.
 * This renders the same styles through the same path the card screenshots use,
 * which does not go through the GPU readback that was suspect.
 */
class TypeRenderTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun specimen() {
        compose.setContent {
            MyApplicationTheme {
                Column(
                    Modifier.fillMaxWidth().background(Color.White).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("CLOSET", style = MaterialTheme.typography.titleLarge)
                    Text("1 ITEM", style = MaterialTheme.typography.labelMedium)
                    Text("EVERY ROUTE", style = MaterialTheme.typography.labelSmall)
                    Text("SHIPPED BY THE SELLER", style = MaterialTheme.typography.labelSmall)
                    Text("Below the usual price here", style = MaterialTheme.typography.titleMedium)
                    Text("A ¥128 jacket", style = MaterialTheme.typography.headlineSmall)
                    Text("Style with intention.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(ctx.filesDir, "type-specimen.png")
        FileOutputStream(out).use {
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
