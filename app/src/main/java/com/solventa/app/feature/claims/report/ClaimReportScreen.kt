package com.solventa.app.feature.claims.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solventa.app.R
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.data.claims.EligiblePolicy
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object ClaimReportTestTags {
    const val STEP = "claim_step"
    const val POLICY_PREFIX = "claim_policy_"
    const val TYPE_PREFIX = "claim_type_"
    const val DESCRIPTION = "claim_description"
    const val PICK_DATE = "claim_pick_date"
    const val PICK_TIME = "claim_pick_time"
    const val CONTINUE = "claim_continue"
    const val SUBMIT = "claim_submit"
    const val ERROR = "claim_error"
    const val EMPTY = "claim_empty"
    const val NUMBER = "claim_number"
    const val ADD_EVIDENCE = "claim_add_evidence"
}

@Composable
fun ClaimReportRoute(
    viewModel: ClaimReportViewModel,
    onEvidence: (claimId: String) -> Unit,
    onHome: () -> Unit,
    onLogin: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        when (val destination = state.destination) {
            is ReportDestination.Evidence -> onEvidence(destination.claimId)
            ReportDestination.Home -> onHome()
            ReportDestination.Login -> onLogin()
            null -> return@LaunchedEffect
        }
        viewModel.consumeDestination()
    }
    BackHandler(enabled = state.step == ReportStep.DETAILS || state.step == ReportStep.REVIEW) { viewModel.back() }

    if (state.step == ReportStep.DONE && state.reported != null) {
        ClaimReportedScreen(
            claim = state.reported!!,
            onAddEvidence = viewModel::goToEvidence,
            onHome = viewModel::goHome,
        )
        return
    }
    ClaimReportScreen(
        state = state,
        selectableDates = viewModel.selectableDates(),
        onSelectPolicy = viewModel::selectPolicy,
        onContinueFromPolicy = viewModel::continueFromPolicy,
        onSelectType = viewModel::selectType,
        onDescriptionChange = viewModel::onDescriptionChange,
        onDateSelected = viewModel::onDateSelected,
        onTimeSelected = viewModel::onTimeSelected,
        onContinueFromDetails = viewModel::continueFromDetails,
        onEditPolicy = viewModel::editPolicy,
        onEditDetails = viewModel::editDetails,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onBack = { if (!viewModel.back()) viewModel.goHome() },
        onHome = viewModel::goHome,
    )
}

@Composable
fun ClaimReportScreen(
    state: ClaimReportUiState,
    selectableDates: ClosedRange<LocalDate>?,
    onSelectPolicy: (String) -> Unit,
    onContinueFromPolicy: () -> Unit,
    onSelectType: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onContinueFromDetails: () -> Unit,
    onEditPolicy: () -> Unit,
    onEditDetails: () -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.claim_report_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        StepIndicator(state.step)
        Spacer(Modifier.height(24.dp))
        when (state.step) {
            ReportStep.POLICY -> PolicyStep(state, onSelectPolicy, onContinueFromPolicy, onRetry, onHome)
            ReportStep.DETAILS -> state.selectedPolicy?.let { policy ->
                DetailsStep(
                    state, policy, selectableDates, onSelectType, onDescriptionChange,
                    onDateSelected, onTimeSelected, onContinueFromDetails,
                )
            }
            ReportStep.REVIEW, ReportStep.SUBMITTING -> state.selectedPolicy?.let { policy ->
                ReviewStep(state, policy, onEditPolicy, onEditDetails, onSubmit, onRetry)
            }
            ReportStep.DONE -> Unit
        }
        if (state.step == ReportStep.DETAILS || state.step == ReportStep.REVIEW) {
            Spacer(Modifier.height(12.dp))
            SolventaSecondaryButton(text = stringResource(R.string.claim_back), onClick = onBack)
        }
    }
}

@Composable
private fun StepIndicator(step: ReportStep) {
    val (index, labelRes) = when (step) {
        ReportStep.POLICY -> 1 to R.string.claim_step_policy
        ReportStep.DETAILS -> 2 to R.string.claim_step_details
        else -> 3 to R.string.claim_step_review
    }
    Text(
        text = stringResource(R.string.claim_step_indicator, index, TOTAL_STEPS, stringResource(labelRes)),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(ClaimReportTestTags.STEP),
    )
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(progress = { index / TOTAL_STEPS.toFloat() }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PolicyStep(
    state: ClaimReportUiState,
    onSelectPolicy: (String) -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onHome: () -> Unit,
) {
    when {
        state.loadingPolicies -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
            Text(stringResource(R.string.claim_policy_loading))
        }
        state.error != null -> {
            ErrorText(state.error)
            Spacer(Modifier.height(16.dp))
            SolventaPrimaryButton(text = stringResource(R.string.claim_retry), onClick = onRetry)
        }
        state.policies.isEmpty() -> Column(Modifier.testTag(ClaimReportTestTags.EMPTY)) {
            Text(stringResource(R.string.claim_policy_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.claim_policy_empty_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            SolventaPrimaryButton(text = stringResource(R.string.claim_go_home), onClick = onHome)
        }
        else -> {
            Text(stringResource(R.string.claim_policy_question), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            state.policies.forEach { policy ->
                PolicyCard(policy, selected = policy.id == state.selectedPolicyId, onClick = { onSelectPolicy(policy.id) })
                Spacer(Modifier.height(8.dp))
            }
            if (ClaimFieldError.POLICY_REQUIRED in state.fieldErrors) {
                FieldError(stringResource(R.string.claim_policy_required))
            }
            Spacer(Modifier.height(16.dp))
            SolventaPrimaryButton(
                text = stringResource(R.string.claim_continue_to_details),
                onClick = onContinue,
                modifier = Modifier.testTag(ClaimReportTestTags.CONTINUE),
            )
        }
    }
}

@Composable
private fun PolicyCard(policy: EligiblePolicy, selected: Boolean, onClick: () -> Unit) {
    Card(
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag(ClaimReportTestTags.POLICY_PREFIX + policy.number),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.padding(start = 12.dp)) {
                Text(policy.productName, style = MaterialTheme.typography.titleSmall)
                Text(policy.number, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.claim_policy_valid_until, formatDate(policy.validTo)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(
    state: ClaimReportUiState,
    policy: EligiblePolicy,
    selectableDates: ClosedRange<LocalDate>?,
    onSelectType: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onContinue: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val errors = state.fieldErrors

    Text(stringResource(R.string.claim_type_question), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        policy.claimTypes.forEach { type ->
            FilterChip(
                selected = type.code == state.selectedTypeCode,
                onClick = { onSelectType(type.code) },
                label = { Text(type.name) },
                modifier = Modifier.testTag(ClaimReportTestTags.TYPE_PREFIX + type.code),
            )
        }
    }
    if (ClaimFieldError.TYPE_REQUIRED in errors) FieldError(stringResource(R.string.claim_type_required))

    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = state.description,
        onValueChange = onDescriptionChange,
        label = { Text(stringResource(R.string.claim_description_label)) },
        placeholder = { Text(stringResource(R.string.claim_description_hint)) },
        minLines = 3,
        maxLines = 6,
        isError = ClaimFieldError.DESCRIPTION_TOO_SHORT in errors || ClaimFieldError.DESCRIPTION_TOO_LONG in errors,
        supportingText = {
            Row(Modifier.fillMaxWidth()) {
                val error = when {
                    ClaimFieldError.DESCRIPTION_TOO_SHORT in errors ->
                        stringResource(R.string.claim_description_too_short, ClaimFormValidator.MIN_DESCRIPTION)
                    ClaimFieldError.DESCRIPTION_TOO_LONG in errors ->
                        stringResource(R.string.claim_description_too_long, ClaimFormValidator.MAX_DESCRIPTION)
                    else -> ""
                }
                Text(error, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.claim_description_counter, state.description.length, ClaimFormValidator.MAX_DESCRIPTION))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ClaimReportTestTags.DESCRIPTION),
    )

    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.claim_when_question), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.testTag(ClaimReportTestTags.PICK_DATE)) {
            Text(state.date?.let(::formatDate) ?: stringResource(R.string.claim_pick_date))
        }
        OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.testTag(ClaimReportTestTags.PICK_TIME)) {
            Text(state.time?.let(::formatTime) ?: stringResource(R.string.claim_pick_time))
        }
    }
    dateErrorRes(errors)?.let { FieldError(stringResource(it)) }

    Spacer(Modifier.height(24.dp))
    SolventaPrimaryButton(
        text = stringResource(R.string.claim_continue_to_review),
        onClick = onContinue,
        modifier = Modifier.testTag(ClaimReportTestTags.CONTINUE),
    )

    if (showDatePicker) {
        ClaimDatePicker(
            initial = state.date,
            range = selectableDates,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                onDateSelected(it)
                showDatePicker = false
            },
        )
    }
    if (showTimePicker) {
        ClaimTimePicker(
            initial = state.time,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                onTimeSelected(it)
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun ReviewStep(
    state: ClaimReportUiState,
    policy: EligiblePolicy,
    onEditPolicy: () -> Unit,
    onEditDetails: () -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
) {
    val submitting = state.step == ReportStep.SUBMITTING
    val typeName = policy.claimTypes.firstOrNull { it.code == state.selectedTypeCode }?.name.orEmpty()
    Text(stringResource(R.string.claim_review_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    ReviewItem(R.string.claim_review_policy, "${policy.productName} · ${policy.number}", onEditPolicy, !submitting)
    ReviewItem(R.string.claim_review_type, typeName, onEditDetails, !submitting)
    ReviewItem(R.string.claim_review_description, state.description.trim(), onEditDetails, !submitting)
    ReviewItem(
        R.string.claim_review_when,
        listOfNotNull(state.date?.let(::formatDate), state.time?.let(::formatTime)).joinToString(" · "),
        onEditDetails,
        !submitting,
    )
    state.error?.let {
        Spacer(Modifier.height(12.dp))
        ErrorText(it)
    }
    Spacer(Modifier.height(24.dp))
    SolventaPrimaryButton(
        text = stringResource(if (state.error == null) R.string.claim_submit else R.string.claim_retry),
        onClick = if (state.error == null) onSubmit else onRetry,
        loading = submitting,
        modifier = Modifier.testTag(ClaimReportTestTags.SUBMIT),
    )
}

@Composable
private fun ReviewItem(labelRes: Int, value: String, onEdit: () -> Unit, editable: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onEdit, enabled = editable) { Text(stringResource(R.string.claim_review_edit)) }
    }
}

@Composable
private fun ErrorText(error: ReportError) {
    Text(
        text = stringResource(errorRes(error)),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(ClaimReportTestTags.ERROR),
    )
}

@Composable
private fun FieldError(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaimDatePicker(
    initial: LocalDate?,
    range: ClosedRange<LocalDate>?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: range?.endInclusive)?.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                range == null || utcTimeMillis.toLocalDate() in range

            override fun isSelectableYear(year: Int): Boolean =
                range == null || year in range.start.year..range.endInclusive.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(it.toLocalDate()) } ?: onDismiss() },
            ) { Text(stringResource(R.string.claim_dialog_accept)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.claim_dialog_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaimTimePicker(initial: LocalTime?, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val now = LocalTime.now()
    val state = rememberTimePickerState(initialHour = (initial ?: now).hour, initialMinute = (initial ?: now).minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.claim_dialog_accept))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.claim_dialog_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

private const val TOTAL_STEPS = 3

private fun dateErrorRes(errors: Set<ClaimFieldError>): Int? = when {
    ClaimFieldError.DATE_REQUIRED in errors -> R.string.claim_date_required
    ClaimFieldError.TIME_REQUIRED in errors -> R.string.claim_time_required
    ClaimFieldError.DATE_OUT_OF_COVERAGE in errors -> R.string.claim_date_out_of_coverage
    ClaimFieldError.DATE_IN_FUTURE in errors -> R.string.claim_date_in_future
    else -> null
}

internal fun errorRes(error: ReportError): Int = when (error) {
    ReportError.LOAD_FAILED -> R.string.claim_error_load
    ReportError.NETWORK -> R.string.claim_error_network
    ReportError.SERVICE_UNAVAILABLE -> R.string.claim_error_service
    ReportError.CLIENT_NOT_REGISTERED -> R.string.claim_error_client
    ReportError.POLICY_NOT_FOUND -> R.string.claim_error_policy_not_found
    ReportError.POLICY_NOT_ACTIVE -> R.string.claim_error_policy_not_active
    ReportError.OUT_OF_COVERAGE -> R.string.claim_error_out_of_coverage
    ReportError.INVALID_DATE -> R.string.claim_error_invalid_date
    ReportError.TYPE_NOT_ALLOWED -> R.string.claim_error_type
    ReportError.INVALID_DATA -> R.string.claim_error_invalid
}

internal fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

internal fun formatTime(time: LocalTime): String = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

// El DatePicker de Material 3 trabaja con medianoche UTC.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
