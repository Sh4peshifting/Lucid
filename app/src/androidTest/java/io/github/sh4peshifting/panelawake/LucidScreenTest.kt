package io.github.sh4peshifting.panelawake

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LucidScreenTest {
    @get:Rule val compose = createComposeRule()
    private val connected = LucidUiState(connected = true, wakefulness = "Awake", panelMode = 0, powered = true, uid = 2000)

    @Test fun switchesOperateIndependently() {
        val state = mutableStateOf(connected)
        compose.setContent {
            LucidTheme(dynamicColor = false) {
                LucidScreen(state.value, {}, { state.value = state.value.copy(keepAwake = it) },
                    { state.value = state.value.copy(panelOff = it) }, {})
            }
        }
        compose.onNodeWithTag("lucid_list").performScrollToNode(hasTestTag("keep_awake"))
        compose.onNodeWithTag("keep_awake").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("lucid_list").performScrollToNode(hasTestTag("panel_off"))
        compose.onNodeWithTag("panel_off").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("keep_awake").assertIsOn()
    }

    @Test fun disconnectedControlsAreDisabled() {
        var requests = 0
        compose.setContent { LucidTheme(dynamicColor = false) { LucidScreen(LucidUiState(), { requests++ }, {}, {}, {}) } }
        compose.onNodeWithTag("connect").performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithTag("lucid_list").performScrollToNode(hasTestTag("keep_awake"))
        compose.onNodeWithTag("keep_awake").assertIsNotEnabled()
        compose.onNodeWithTag("lucid_list").performScrollToNode(hasTestTag("panel_off"))
        compose.onNodeWithTag("panel_off").assertIsNotEnabled()
    }

    @Test fun restoreRequiresConfirmation() {
        var restores = 0
        compose.setContent { LucidTheme(dynamicColor = false) { LucidScreen(connected, {}, {}, {}, { restores++ }) } }
        compose.onNodeWithTag("lucid_list").performScrollToNode(hasTestTag("restore"))
        compose.onNodeWithTag("restore").performClick()
        compose.runOnIdle { assertEquals(0, restores) }
        compose.onNodeWithText("停止并恢复").performClick()
        compose.runOnIdle { assertEquals(1, restores) }
    }

    @Test fun capturesMaterialThreeLightAndDarkScreens() {
        val dark = mutableStateOf(false)
        compose.setContent {
            LucidTheme(darkTheme = dark.value, dynamicColor = false) {
                LucidScreen(connected.copy(keepAwake = true, panelOff = true), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("已断电").assertExists()
        compose.onNodeWithText("清醒中").assertExists()
        capture("lucid-light.png")
        compose.runOnIdle { dark.value = true }
        compose.waitForIdle()
        capture("lucid-dark.png")
    }

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
