package com.solventa.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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
import com.solventa.app.feature.login.LoginRoute
import com.solventa.app.feature.login.LoginViewModel

object Routes {
    const val LOGIN = "login"
    const val ACCOUNT_NOT_FOUND = "account-not-found"
    const val BIOMETRIC_OFFER = "biometric-offer"
    const val HOME = "home"
    const val SECURITY = "security"
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

    Box(modifier) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Routes.LOGIN) {
                LoginRoute(
                    viewModel = viewModel(factory = LoginViewModel.factory(container)),
                    onLoggedIn = ::goToHome,
                    onBiometricOffer = {
                        navController.navigate(Routes.BIOMETRIC_OFFER) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onAccountNotFound = { navController.navigate(Routes.ACCOUNT_NOT_FOUND) },
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
                    onOpenSecurity = { navController.navigate(Routes.SECURITY) },
                    onLogout = {
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
                onUnlocked = {},
                onUsePassword = ::goToLogin,
            )
        }
    }
}
