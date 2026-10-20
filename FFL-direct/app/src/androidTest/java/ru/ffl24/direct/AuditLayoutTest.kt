package ru.ffl24.direct

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.ffl24.direct.domain.*
import ru.ffl24.direct.ui.OrdersContent
import ru.ffl24.direct.ui.theme.DirectTheme
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap

/** Small phone width plus large Cyrillic text; no personal database rows rendered. */
class AuditLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun smallLightPhoneKeepsAllActionsInsideTheScreen() = checkActions(false,1f)
    @Test fun smallDarkPhoneWithLargeFontKeepsAllActionsInsideTheScreen() = checkActions(true,1.6f)

    private fun checkActions(dark: Boolean, font: Float) {
        var palette: ColorScheme?=null
        val row=DriverOrder(OrderId(OrderSource.REYS,1),97,"Тестовый водитель","2099-12-31",
            "FFL-42","Длинное русское название проверочного заказчика на маленьком экране",
            "Примечание для проверки увеличенного шрифта",0,3,2,1,false,null,OrderContacts())
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,font)) {
                DirectTheme(dark) {
                    palette=MaterialTheme.colorScheme
                    Surface { Box(Modifier.width(320.dp)) {
                        OrdersContent(DetailsResult.Success(listOf(row)),"2099-12-31",false,null,{},{},{_,_,_->},{},{})
                    } }
                }
            }
        }
        compose.runOnIdle {
            val colors=requireNotNull(palette)
            assertTrue("Text-button contrast must be at least 4.5:1",contrast(colors.primary,colors.surface)>=4.5)
            assertTrue("Filled-button text contrast must be at least 4.5:1",contrast(colors.onPrimary,colors.primary)>=4.5)
        }
        val container=compose.onNodeWithTag("orders").fetchSemanticsNode().boundsInRoot
        for (section in 0..2) {
            compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("section_$section"))
            compose.onNodeWithTag("section_$section").assertIsDisplayed()
            val bounds=compose.onNodeWithTag("section_$section").fetchSemanticsNode().boundsInRoot
            assertTrue("Section fits phone width",bounds.left>=container.left && bounds.right<=container.right)
        }
        compose.onNodeWithTag("order_list").performScrollToNode(hasTestTag("order_reys:1"))
        val rowBounds=compose.onNodeWithTag("order_reys:1").fetchSemanticsNode().boundsInRoot
        assertTrue("Compact row fits phone width",rowBounds.left>=container.left && rowBounds.right<=container.right)
        compose.onNodeWithContentDescription("Телефон не указан").assertExists()
        val folder=java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"audit-ui").apply { mkdirs() }
        java.io.File(folder,if(dark) "small-dark-large.png" else "small-light.png").outputStream().use {
            compose.onNodeWithTag("orders").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
    private fun contrast(a: Color,b: Color): Double {
        fun luminance(c: Color): Double {
            fun linear(v: Float): Double = if(v<=0.04045f) v/12.92 else Math.pow((v+0.055)/1.055,2.4)
            return 0.2126*linear(c.red)+0.7152*linear(c.green)+0.0722*linear(c.blue)
        }
        val x=luminance(a);val y=luminance(b)
        return (maxOf(x,y)+0.05)/(minOf(x,y)+0.05)
    }
}
