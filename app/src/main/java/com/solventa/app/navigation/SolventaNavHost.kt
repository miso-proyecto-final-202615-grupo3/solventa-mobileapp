package com.solventa.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.solventa.app.BuildConfig
import com.solventa.app.di.AppContainer
import com.solventa.app.feature.account.AccountNotFoundScreen
import com.solventa.app.feature.biometric.BiometricOfferRoute
import com.solventa.app.feature.biometric.BiometricOfferViewModel
import com.solventa.app.feature.biometric.SecuritySettingsRoute
import com.solventa.app.feature.biometric.SecuritySettingsViewModel
import com.solventa.app.feature.biometric.UnlockRoute
import com.solventa.app.feature.biometric.UnlockViewModel
import com.solventa.app.feature.home.HomeScreen
import com.solventa.app.R
import com.solventa.app.feature.activation.ActivationRoute
import com.solventa.app.feature.activation.ActivationViewModel
import android.Manifest
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.solventa.app.core.permissions.isAnyGranted
import com.solventa.app.feature.claims.detail.ClaimDetailRoute
import com.solventa.app.feature.claims.detail.ClaimDetailViewModel
import com.solventa.app.feature.claims.list.MyClaimsRoute
import com.solventa.app.feature.claims.list.MyClaimsViewModel
import com.solventa.app.feature.claims.evidence.ClaimEvidenceRoute
import com.solventa.app.feature.claims.evidence.ClaimEvidenceViewModel
import com.solventa.app.feature.claims.report.ClaimReportRoute
import com.solventa.app.feature.claims.report.ClaimReportViewModel
import com.solventa.app.feature.consent.BiometricConsentRoute
import com.solventa.app.feature.consent.BiometricConsentViewModel
import com.solventa.app.feature.consent.ConsentDeclinedScreen
import com.solventa.app.feature.login.LoginRoute
import com.solventa.app.feature.login.LoginViewModel

object Routes {
    const val LOGIN = "login"
    const val ACCOUNT_NOT_FOUND = "account-not-found"
    const val BIOMETRIC_OFFER = "biometric-offer"
    const val HOME = "home"
    const val SECURITY = "security"
    const val BIOMETRIC_CONSENT = "biometric-consent"
    const val CONSENT_DECLINED = "consent-declined"
    const val ACTIVATION = "activation"
    const val CLAIM_REPORT = "claims/report"
    const val CLAIM_EVIDENCE = "claims/{claimId}/evidence"
    const val MY_CLAIMS = "claims"
    const val CLAIM_DETAIL = "claims/{claimId}"

    fun claimEvidence(claimId: String) = "claims/$claimId/evidence"
    fun claimDetail(claimId: String) = "claims/$claimId"
}

@Composable
fun SolventaNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val locked by container.appLockManager.locked.collectAsStateWithLifecycle()
    val pendingClaimId by container.pendingDeepLink.claimId.collectAsStateWithLifecycle()
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    // Con biometría activa la app arranca bloqueada sobre Inicio; la sesión se recupera al desbloquear.
    val startDestination = remember { if (locked) Routes.HOME else Routes.LOGIN }

    fun goToLogin() = navController.navigate(Routes.LOGIN) {
        popUpTo(navController.graph.id) { inclusive = true }
    }

    fun goToHome() = navController.navigate(Routes.HOME) {
        popUpTo(navController.graph.id) { inclusive = true }
    }

    LaunchedEffect(pendingClaimId, locked, session) {
        if (!locked && session != null) {
            container.pendingDeepLink.consume()?.let { claimId ->
                navController.navigate(Routes.claimDetail(claimId))
            }
        }
    }

    // Consentimiento biométrico → activación: el teléfono sin vincular no puede saltarse este flujo.
    fun goToActivationFlow() = navController.navigate(Routes.BIOMETRIC_CONSENT) {
        popUpTo(navController.graph.id) { inclusive = true }
    }

    Box(modifier) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Routes.LOGIN) {
                val uriHandler = LocalUriHandler.current
                LoginRoute(
                    viewModel = viewModel(factory = LoginViewModel.factory(container)),
                    onLoggedIn = ::goToHome,
                    onActivationRequired = ::goToActivationFlow,
                    onBiometricOffer = {
                        navController.navigate(Routes.BIOMETRIC_OFFER) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onAccountNotFound = { navController.navigate(Routes.ACCOUNT_NOT_FOUND) },
                    onGoToWebSignup = { uriHandler.openUri(BuildConfig.WEB_SIGNUP_URL) },
                    // La biometría sigue configurada tras cerrar sesión: se puede entrar sin contraseña.
                    biometricEntryAvailable = container.biometricVault.isEnabled(),
                    onBiometricEntry = container.appLockManager::lock,
                )
            }
            composable(Routes.ACCOUNT_NOT_FOUND) {
                val uriHandler = LocalUriHandler.current
                AccountNotFoundScreen(
                    onGoToWebSignup = { uriHandler.openUri(BuildConfig.WEB_SIGNUP_URL) },
                    onTryAnotherEmail = { navController.popBackStack() },
                )
            }
            composable(Routes.BIOMETRIC_OFFER) {
                BiometricOfferRoute(
                    viewModel = viewModel(factory = BiometricOfferViewModel.factory(container)),
                    onFinished = ::goToHome,
                )
            }
            composable(Routes.HOME) {
                val session by container.sessionStore.session.collectAsStateWithLifecycle()
                val pendingEvidence by container.evidenceUploadQueue.pendingClaims().collectAsStateWithLifecycle(emptyList())
                val biometricEnabled = container.biometricVault.isEnabled()
                HomeScreen(
                    clientName = session?.cliente?.nombre.orEmpty(),
                    deviceLinked = session?.cliente?.dispositivoVinculado == true,
                    showBiometricHint = !biometricEnabled && container.biometricSettings.offerDeclined,
                    onActivateDevice = ::goToActivationFlow,
                    onReportClaim = { navController.navigate(Routes.CLAIM_REPORT) },
                    onOpenMyClaims = { navController.navigate(Routes.MY_CLAIMS) },
                    pendingEvidenceClaimNumber = pendingEvidence.firstOrNull()?.second,
                    onOpenPendingEvidence = {
                        pendingEvidence.firstOrNull()?.let { navController.navigate(Routes.claimEvidence(it.first)) }
                    },
                    onOpenSecurity = { navController.navigate(Routes.SECURITY) },
                    onLogout = {
                        container.logout()
                        goToLogin()
                    },
                )
            }
            composable(Routes.BIOMETRIC_CONSENT) {
                val termsVersion = stringResource(R.string.biometric_consent_terms_version)
                BiometricConsentRoute(
                    viewModel = viewModel(factory = BiometricConsentViewModel.factory(container, termsVersion)),
                    onAccepted = {
                        navController.navigate(Routes.ACTIVATION) {
                            popUpTo(Routes.BIOMETRIC_CONSENT) { inclusive = true }
                        }
                    },
                    onDeclined = {
                        navController.navigate(Routes.CONSENT_DECLINED) {
                            popUpTo(Routes.BIOMETRIC_CONSENT) { inclusive = true }
                        }
                    },
                    onSessionExpired = {
                        container.logout()
                        goToLogin()
                    },
                )
            }
            composable(Routes.CONSENT_DECLINED) {
                ConsentDeclinedScreen(onBackToLogin = {
                    container.logout()
                    goToLogin()
                })
            }
            composable(Routes.ACTIVATION) {
                val uriHandler = LocalUriHandler.current
                ActivationRoute(
                    viewModel = viewModel(factory = ActivationViewModel.factory(container)),
                    launchers = container.captureLauncherFactory,
                    onBiometricOffer = {
                        navController.navigate(Routes.BIOMETRIC_OFFER) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                    onHome = ::goToHome,
                    onLogin = {
                        container.logout()
                        goToLogin()
                    },
                    onAccountNotFound = { uriHandler.openUri(BuildConfig.WEB_SIGNUP_URL) },
                    onConsentRequired = ::goToActivationFlow,
                )
            }
            composable(Routes.CLAIM_REPORT) {
                ClaimReportRoute(
                    viewModel = viewModel(factory = ClaimReportViewModel.factory(container)),
                    onEvidence = { claimId ->
                        navController.navigate(Routes.claimEvidence(claimId)) {
                            popUpTo(Routes.CLAIM_REPORT) { inclusive = true }
                        }
                    },
                    onHome = ::goToHome,
                    onLogin = {
                        container.logout()
                        goToLogin()
                    },
                )
            }
            composable(
                Routes.CLAIM_EVIDENCE,
                arguments = listOf(navArgument("claimId") { type = NavType.StringType }),
            ) { entry ->
                val claimId = entry.arguments?.getString("claimId").orEmpty()
                val context = LocalContext.current
                ClaimEvidenceRoute(
                    viewModel = viewModel(
                        factory = ClaimEvidenceViewModel.factory(container, claimId) {
                            context.isAnyGranted(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        },
                    ),
                    uriFor = container.evidenceFileStore::uriFor,
                    onHome = ::goToHome,
                    onLogin = {
                        container.logout()
                        goToLogin()
                    },
                )
            }
            composable(Routes.MY_CLAIMS) {
                MyClaimsRoute(
                    viewModel = viewModel(factory = MyClaimsViewModel.factory(container)),
                    onOpenClaim = { claimId -> navController.navigate(Routes.claimDetail(claimId)) },
                    onReportClaim = { navController.navigate(Routes.CLAIM_REPORT) },
                    onBack = { navController.popBackStack() },
                    onLogin = {
                        container.logout()
                        goToLogin()
                    },
                )
            }
            composable(
                Routes.CLAIM_DETAIL,
                arguments = listOf(navArgument("claimId") { type = NavType.StringType }),
            ) { entry ->
                val claimId = entry.arguments?.getString("claimId").orEmpty()
                ClaimDetailRoute(
                    viewModel = viewModel(factory = ClaimDetailViewModel.factory(container, claimId)),
                    onAddEvidence = { id -> navController.navigate(Routes.claimEvidence(id)) },
                    onBack = { navController.popBackStack() },
                    onLogin = {
                        container.logout()
                        goToLogin()
                    },
                )
            }
            composable(Routes.SECURITY) {
                SecuritySettingsRoute(
                    viewModel = viewModel(factory = SecuritySettingsViewModel.factory(container)),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        if (locked) {
            UnlockRoute(
                viewModel = viewModel(factory = UnlockViewModel.factory(container)),
                // Al abrir o volver a la app ya hay una pantalla debajo; solo desde el login hay que ir a Inicio.
                onUnlocked = { if (navController.currentDestination?.route == Routes.LOGIN) goToHome() },
                onUsePassword = ::goToLogin,
            )
        }
    }
}
