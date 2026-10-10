package com.solventa.app.feature.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solventa.app.R
import com.solventa.app.core.designsystem.theme.SolventaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun escribirCredencialesYEnviar() {
        var state by mutableStateOf(LoginUiState())
        var submitted = false
        composeRule.setContent {
            SolventaTheme {
                LoginScreen(
                    uiState = state,
                    onEmailChange = { state = state.copy(email = it) },
                    onPasswordChange = { state = state.copy(password = it) },
                    onTogglePasswordVisibility = {},
                    onSubmit = { submitted = true },
                )
            }
        }

        composeRule.onNodeWithTag(LoginTestTags.EMAIL).performTextInput("camila.rojas@correo.com")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD).performTextInput("secreta")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).performClick()

        assertEquals("camila.rojas@correo.com", state.email)
        assertEquals("secreta", state.password)
        assertTrue(submitted)
    }

    @Test
    fun muestraErrorDeCredencialesInvalidas() {
        composeRule.setContent {
            SolventaTheme {
                LoginScreen(
                    uiState = LoginUiState(formError = LoginFormError.INVALID_CREDENTIALS),
                    onEmailChange = {},
                    onPasswordChange = {},
                    onTogglePasswordVisibility = {},
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithTag(LoginTestTags.FORM_ERROR).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.login_error_invalid_credentials)).assertIsDisplayed()
    }

    @Test
    fun botonDeVisibilidadCambiaSuTexto() {
        composeRule.setContent {
            SolventaTheme {
                LoginScreen(
                    uiState = LoginUiState(passwordVisible = true),
                    onEmailChange = {},
                    onPasswordChange = {},
                    onTogglePasswordVisibility = {},
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.login_hide_password)).assertIsDisplayed()
    }
}
