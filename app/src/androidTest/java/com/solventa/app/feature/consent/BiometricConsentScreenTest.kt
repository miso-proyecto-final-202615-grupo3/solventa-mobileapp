package com.solventa.app.feature.consent

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solventa.app.core.designsystem.theme.SolventaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiometricConsentScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun continuarDeshabilitadoHastaMarcarElCheckbox() {
        composeRule.setContent {
            SolventaTheme {
                BiometricConsentScreen(
                    uiState = BiometricConsentUiState(loading = false, termsVersion = "1.0", checked = false),
                    onCheckedChange = {}, onAccept = {}, onReject = {}, onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(BiometricConsentTestTags.ACCEPT).assertIsNotEnabled()
    }

    @Test
    fun conCheckboxMarcadoSePuedeAceptarYRechazar() {
        var rejected = false
        composeRule.setContent {
            SolventaTheme {
                BiometricConsentScreen(
                    uiState = BiometricConsentUiState(loading = false, termsVersion = "1.0", checked = true),
                    onCheckedChange = {}, onAccept = {}, onReject = { rejected = true }, onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(BiometricConsentTestTags.ACCEPT).assertIsEnabled()
        composeRule.onNodeWithTag(BiometricConsentTestTags.REJECT).performClick()
        assertTrue(rejected)
    }
}
