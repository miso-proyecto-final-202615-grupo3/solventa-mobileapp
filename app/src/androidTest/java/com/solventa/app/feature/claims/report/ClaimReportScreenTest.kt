package com.solventa.app.feature.claims.report

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solventa.app.R
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.data.claims.MockClaimsRepository
import com.solventa.app.data.claims.ReportedClaim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ClaimReportScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val travel = MockClaimsRepository.POLICIES.first()

    private fun setScreen(state: ClaimReportUiState, onSelectPolicy: (String) -> Unit = {}, onSubmit: () -> Unit = {}) {
        composeRule.setContent {
            SolventaTheme {
                ClaimReportScreen(
                    state = state,
                    selectableDates = null,
                    onSelectPolicy = onSelectPolicy,
                    onContinueFromPolicy = {},
                    onSelectType = {},
                    onDescriptionChange = {},
                    onDateSelected = {},
                    onTimeSelected = {},
                    onContinueFromDetails = {},
                    onEditPolicy = {},
                    onEditDetails = {},
                    onSubmit = onSubmit,
                    onRetry = {},
                    onBack = {},
                    onHome = {},
                )
            }
        }
    }

    @Test
    fun listaLasPolizasYPermiteElegirUna() {
        var selected: String? = null
        setScreen(
            ClaimReportUiState(loadingPolicies = false, policies = MockClaimsRepository.POLICIES),
            onSelectPolicy = { selected = it },
        )

        composeRule.onNodeWithTag(ClaimReportTestTags.POLICY_PREFIX + "SLV-2026-09120").performClick()

        assertEquals(MockClaimsRepository.POLICIES[1].id, selected)
    }

    @Test
    fun sinPolizasMuestraElEstadoVacio() {
        setScreen(ClaimReportUiState(loadingPolicies = false, policies = emptyList()))

        composeRule.onNodeWithTag(ClaimReportTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun detallesMuestranLosTiposDeLaPolizaYLosErrores() {
        setScreen(
            ClaimReportUiState(
                step = ReportStep.DETAILS,
                loadingPolicies = false,
                policies = MockClaimsRepository.POLICIES,
                selectedPolicyId = travel.id,
                fieldErrors = setOf(ClaimFieldError.TYPE_REQUIRED, ClaimFieldError.DATE_REQUIRED),
            ),
        )

        composeRule.onNodeWithTag(ClaimReportTestTags.TYPE_PREFIX + "GASTO_MEDICO").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.claim_type_required)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.claim_date_required)).assertIsDisplayed()
    }

    @Test
    fun revisarEnviaYMuestraElErrorConReintento() {
        var submitted = false
        setScreen(
            ClaimReportUiState(
                step = ReportStep.REVIEW,
                loadingPolicies = false,
                policies = MockClaimsRepository.POLICIES,
                selectedPolicyId = travel.id,
                selectedTypeCode = "GASTO_MEDICO",
                description = "Me atendieron en urgencias.",
                date = LocalDate.of(2026, 11, 10),
                time = LocalTime.of(9, 30),
            ),
            onSubmit = { submitted = true },
        )

        composeRule.onNodeWithText(context.getString(R.string.claim_submit)).performClick()

        assertTrue(submitted)
    }

    @Test
    fun confirmacionMuestraElFolioYLosDosBotones() {
        var evidence = false
        composeRule.setContent {
            SolventaTheme {
                ClaimReportedScreen(
                    claim = ReportedClaim(
                        id = "c41f0e2a", number = "SIN-2026-C41F0E2A", status = ClaimStatus.REPORTED,
                        policyId = travel.id, policyNumber = travel.number, typeCode = "GASTO_MEDICO",
                        occurredAt = Instant.parse("2026-11-10T14:30:00Z"), reportedAt = Instant.parse("2026-11-10T20:00:00Z"),
                    ),
                    onAddEvidence = { evidence = true },
                    onHome = {},
                )
            }
        }

        composeRule.onNodeWithTag(ClaimReportTestTags.NUMBER).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.claim_go_home)).assertIsDisplayed()
        composeRule.onNodeWithTag(ClaimReportTestTags.ADD_EVIDENCE).performClick()
        assertTrue(evidence)
    }
}
