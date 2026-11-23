package com.solventa.app.feature.claims.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solventa.app.R
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.data.claims.ClaimChannel
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.MockClaimsRepository
import com.solventa.app.data.claims.ReportedClaim
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class MyClaimsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val viaje = MockClaimsRepository.POLICIES[0]
    private val claim = ReportedClaim(
        id = "c-1", number = "SIN-2026-ABCDEF01", status = ClaimStatus.REPORTED, policyId = viaje.id,
        policyNumber = viaje.number, typeCode = "GASTO_MEDICO", occurredAt = Instant.EPOCH, reportedAt = Instant.EPOCH,
    )

    private fun setScreen(state: MyClaimsUiState, onOpen: (String) -> Unit = {}, onFilter: (String?) -> Unit = {}) {
        composeRule.setContent {
            SolventaTheme {
                MyClaimsScreen(
                    state = state, onSelectPolicy = onFilter, onOpenClaim = onOpen, onLoadMore = {}, onRetry = {},
                    onReportClaim = {}, onBack = {},
                )
            }
        }
    }

    @Test
    fun muestraElSiniestroConSuTipoEstadoYAbreElDetalle() {
        var opened: String? = null
        setScreen(MyClaimsUiState(loading = false, policies = MockClaimsRepository.POLICIES, claims = listOf(claim), total = 1)) { opened = it }

        composeRule.onNodeWithText("SIN-2026-ABCDEF01").assertIsDisplayed()
        composeRule.onNodeWithText("Gasto médico").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.claim_status_reported)).assertIsDisplayed()
        composeRule.onNodeWithTag(MyClaimsTestTags.ITEM_PREFIX + "c-1").performClick()
        assertEquals("c-1", opened)
    }

    @Test
    fun filtroPorPoliza() {
        var selected: String? = "sin-tocar"
        setScreen(MyClaimsUiState(loading = false, policies = MockClaimsRepository.POLICIES, claims = listOf(claim), total = 1), onFilter = { selected = it })

        composeRule.onNodeWithTag(MyClaimsTestTags.FILTER_PREFIX + viaje.id).performClick()
        assertEquals(viaje.id, selected)
        composeRule.onNodeWithTag(MyClaimsTestTags.FILTER_ALL).performClick()
        assertEquals(null, selected)
    }

    @Test
    fun estadoVacioYError() {
        setScreen(MyClaimsUiState(loading = false))
        composeRule.onNodeWithTag(MyClaimsTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun errorSinConexion() {
        setScreen(MyClaimsUiState(loading = false, error = MyClaimsError.NETWORK))
        composeRule.onNodeWithText(context.getString(R.string.my_claims_error_network)).assertIsDisplayed()
    }

    @Test
    fun avisoDeNotificacionesDesactivadas() {
        var enabled = false
        composeRule.setContent {
            SolventaTheme {
                MyClaimsScreen(
                    state = MyClaimsUiState(loading = false), onSelectPolicy = {}, onOpenClaim = {}, onLoadMore = {},
                    onRetry = {}, onReportClaim = {}, onBack = {},
                    notificationsOff = true, onEnableNotifications = { enabled = true },
                )
            }
        }

        composeRule.onNodeWithTag(MyClaimsTestTags.NOTIFICATIONS_OFF).assertIsDisplayed()
        composeRule.onNodeWithTag(MyClaimsTestTags.ENABLE_NOTIFICATIONS).performClick()
        assertEquals(true, enabled)
    }

    @Test
    fun siniestroAutomaticoSeMarcaEnLaLista() {
        val automatic = claim.copy(id = "c-2", typeCode = "LLUVIA_EXTREMA", status = ClaimStatus.APPROVED, channel = ClaimChannel.PARAMETRIC)
        setScreen(MyClaimsUiState(loading = false, claims = listOf(automatic), total = 1))

        composeRule.onNodeWithTag(MyClaimsTestTags.AUTOMATIC_PREFIX + "c-2").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.claim_type_heavy_rain)).assertIsDisplayed()
    }
}
