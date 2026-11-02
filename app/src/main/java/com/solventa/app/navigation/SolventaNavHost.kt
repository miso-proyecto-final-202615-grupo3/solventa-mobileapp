package com.solventa.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
}

@Composable
fun SolventaNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val locked by container.appLockManager.locked.collectAsStateWithLifecycle()
    // Con biometría activa la app arranca bloqueada sobre Inicio; la sesión se recupera al desbloquear.
    val startDestination = remember { if (locked) Routes.HOME else Routes.LOGIN }

    fun goToLogin() = navController.navigate(Routes.LOGIN) {
        popUpTo(navController.graph.id) { inclusive = true }
    }

    fun goToHome() = navController.navigate(Routes.HOME) {
        popUpTo(navController.graph.id) { inclusive = true }
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
                val biometricEnabled = container.biometricVault.isEnabled()
                HomeScreen(
                    clientName = session?.cliente?.nombre.orEmpty(),
                    deviceLinked = session?.cliente?.dispositivoVinculado == true,
                    showBiometricHint = !biometricEnabled && container.biometricSettings.offerDeclined,
                    onActivateDevice = ::goToActivationFlow,
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
