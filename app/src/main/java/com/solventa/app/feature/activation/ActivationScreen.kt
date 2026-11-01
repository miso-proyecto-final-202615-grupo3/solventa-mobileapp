package com.solventa.app.feature.activation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solventa.app.R
import com.solventa.app.core.camera.FrontCameraPreview
import com.solventa.app.core.camera.SecureWindowEffect
import com.solventa.app.core.designsystem.component.SolventaPrimaryButton
import com.solventa.app.core.designsystem.component.SolventaSecondaryButton
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.core.liveness.FaceHint
import com.solventa.app.core.liveness.GuideState
import com.solventa.app.core.liveness.LivenessChallenge
import com.solventa.app.core.liveness.MlKitFaceAnalyzer
import com.solventa.app.core.liveness.launcher.CaptureLauncherFactory
import com.solventa.app.core.liveness.launcher.DiditCaptureLauncher
import com.solventa.app.data.activation.CaptureType
import com.solventa.app.data.activation.SdkStatus

object ActivationTestTags {
    const val START = "activation_start"
    const val GUIDE = "activation_guide"
    const val RESULT_TITLE = "activation_result_title"
    const val RETRY = "activation_retry"
    const val CONTINUE = "activation_continue"
    const val BACK_TO_LOGIN = "activation_back_to_login"
    const val REMAINING = "activation_remaining"
}

@Composable
fun ActivationRoute(
    viewModel: ActivationViewModel,
    launchers: CaptureLauncherFactory,
    onBiometricOffer: () -> Unit,
    onHome: () -> Unit,
    onLogin: () -> Unit,
    onAccountNotFound: () -> Unit,
    onConsentRequired: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val hasFrontCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        permanentlyDenied = !granted && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
        if (granted) viewModel.onStart()
    }

    LaunchedEffect(uiState.destination) {
        when (uiState.destination) {
            ActivationDestination.BIOMETRIC_OFFER -> onBiometricOffer()
            ActivationDestination.HOME -> onHome()
            ActivationDestination.LOGIN -> onLogin()
            ActivationDestination.ACCOUNT_NOT_FOUND -> onAccountNotFound()
            ActivationDestination.CONSENT -> onConsentRequired()
            null -> return@LaunchedEffect
        }
        viewModel.onDestinationHandled()
    }

    // Captura con SDK (Didit): se lanza la estrategia que soporta la sesión.
    LaunchedEffect(uiState.step, uiState.sdkToken) {
        val token = uiState.sdkToken
        if (uiState.step != ActivationStep.LAUNCHING_SDK || token == null || activity == null) return@LaunchedEffect
        val launcher = launchers.forType(CaptureType.Sdk(DiditCaptureLauncher.SDK_NAME, token)) as? DiditCaptureLauncher
        viewModel.onSdkResult(launcher?.launch(activity, token) ?: SdkStatus.FAILED)
    }

    when (uiState.step) {
        ActivationStep.INTRO -> ActivationIntroScreen(
            hasFrontCamera = hasFrontCamera,
            permissionDenied = permissionDenied,
            permanentlyDenied = permanentlyDenied,
            onStart = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.onStart() else permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
        ActivationStep.STARTING -> ActivationProgress(stringResource(R.string.activation_starting))
        ActivationStep.LAUNCHING_SDK -> ActivationProgress(stringResource(R.string.activation_launching_sdk))
        ActivationStep.SUBMITTING -> ActivationProgress(stringResource(R.string.activation_submitting))
        ActivationStep.CAPTURE -> {
            SecureWindowEffect()
            val analyzer = remember(viewModel) {
                MlKitFaceAnalyzer(onSignals = viewModel::onFaceSignals, onFrameCaptured = viewModel::onFrameCaptured)
            }
            DisposableEffect(analyzer) { onDispose { analyzer.close() } }
            LivenessCaptureScreen(
                guide = uiState.guide,
                onCancel = viewModel::onCancel,
                cameraPreview = { modifier -> FrontCameraPreview(analyzer = analyzer, modifier = modifier) },
            )
        }
        ActivationStep.RESULT -> ActivationResultScreen(
            outcome = uiState.outcome,
            remainingAttempts = uiState.remainingAttempts,
            onRetry = viewModel::onRetry,
            onContinue = onHome,
            onBackToLogin = onLogin,
        )
    }
}

@Composable
fun ActivationIntroScreen(
    hasFrontCamera: Boolean,
    permissionDenied: Boolean,
    permanentlyDenied: Boolean,
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.activation_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.activation_intro_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.activation_intro_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.activation_intro_tips),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasFrontCamera) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.activation_no_front_camera), color = MaterialTheme.colorScheme.error)
            return@Column
        }
        if (permissionDenied) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.activation_permission_denied),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(32.dp))
        SolventaPrimaryButton(
            text = stringResource(R.string.activation_intro_start),
            onClick = onStart,
            modifier = Modifier.testTag(ActivationTestTags.START),
        )
        if (permanentlyDenied) {
            Spacer(Modifier.height(12.dp))
            SolventaSecondaryButton(text = stringResource(R.string.activation_open_settings), onClick = onOpenSettings)
        }
    }
}

/** Recibe [GuideState] como parámetro para poder probar sus textos sin cámara. */
@Composable
fun LivenessCaptureScreen(
    guide: GuideState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPreview: @Composable (Modifier) -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(percent = 50))
                .border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(percent = 50)),
        ) {
            cameraPreview(Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = guideText(guide),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .testTag(ActivationTestTags.GUIDE)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(24.dp))
        SolventaSecondaryButton(text = stringResource(R.string.activation_cancel), onClick = onCancel)
    }
}

@Composable
private fun guideText(guide: GuideState): String = when (guide) {
    GuideState.WaitingForFace -> stringResource(R.string.guide_waiting_face)
    is GuideState.AdjustFace -> stringResource(
        when (guide.hint) {
            FaceHint.NO_FACE -> R.string.guide_no_face
            FaceHint.TOO_FAR -> R.string.guide_too_far
            FaceHint.NOT_CENTERED -> R.string.guide_not_centered
            FaceHint.MULTIPLE_FACES -> R.string.guide_multiple_faces
        },
    )
    is GuideState.HoldStill -> stringResource(R.string.guide_hold_still)
    is GuideState.ChallengeInProgress -> stringResource(
        when (guide.challenge) {
            LivenessChallenge.BLINK -> R.string.guide_blink
            LivenessChallenge.TURN_LEFT -> R.string.guide_turn_left
            LivenessChallenge.TURN_RIGHT -> R.string.guide_turn_right
        },
    )
    GuideState.ReturnToCenter -> stringResource(R.string.guide_return_center)
    GuideState.Completed -> stringResource(R.string.guide_completed)
    is GuideState.Failed -> stringResource(R.string.result_local_title)
}

@Composable
fun ActivationResultScreen(
    outcome: ActivationOutcome?,
    remainingAttempts: Int?,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, body) = when (outcome) {
        ActivationOutcome.APPROVED -> R.string.result_approved_title to R.string.result_approved_body
        ActivationOutcome.REJECTED -> R.string.result_rejected_title to R.string.result_rejected_body
        ActivationOutcome.EXHAUSTED -> R.string.result_exhausted_title to R.string.result_exhausted_body
        ActivationOutcome.NETWORK -> R.string.result_network_title to R.string.result_network_body
        ActivationOutcome.LOCAL_FAILURE -> R.string.result_local_title to R.string.result_local_body
        ActivationOutcome.INVALID_CAPTURE -> R.string.result_invalid_capture_title to R.string.result_invalid_capture_body
        ActivationOutcome.ACCOUNT_NOT_FOUND, ActivationOutcome.SERVICE_UNAVAILABLE, null ->
            R.string.result_service_title to R.string.result_service_body
    }
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .testTag(ActivationTestTags.RESULT_TITLE)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (outcome == ActivationOutcome.REJECTED && remainingAttempts != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = pluralStringResource(R.plurals.result_remaining, remainingAttempts, remainingAttempts),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(ActivationTestTags.REMAINING),
            )
        }
        Spacer(Modifier.height(32.dp))
        when (outcome) {
            ActivationOutcome.APPROVED -> SolventaPrimaryButton(
                text = stringResource(R.string.result_continue),
                onClick = onContinue,
                modifier = Modifier.testTag(ActivationTestTags.CONTINUE),
            )
            ActivationOutcome.EXHAUSTED -> SolventaPrimaryButton(
                text = stringResource(R.string.result_back_login),
                onClick = onBackToLogin,
                modifier = Modifier.testTag(ActivationTestTags.BACK_TO_LOGIN),
            )
            else -> {
                SolventaPrimaryButton(
                    text = stringResource(R.string.result_retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag(ActivationTestTags.RETRY),
                )
                Spacer(Modifier.height(12.dp))
                SolventaSecondaryButton(text = stringResource(R.string.result_back_login), onClick = onBackToLogin)
            }
        }
    }
}

@Composable
private fun ActivationProgress(label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7F4)
@Composable
private fun ActivationResultPreview() {
    SolventaTheme {
        ActivationResultScreen(ActivationOutcome.REJECTED, 2, onRetry = {}, onContinue = {}, onBackToLogin = {})
    }
}
