package com.solventa.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.solventa.app.feature.home.HomeScreen
import com.solventa.app.feature.login.LoginRoute
import com.solventa.app.feature.login.LoginViewModel

object Routes {
    const val LOGIN = "login"
    const val ACCOUNT_NOT_FOUND = "account-not-found"
    const val HOME = "home"
}

@Composable
fun SolventaNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.LOGIN, modifier = modifier) {
        composable(Routes.LOGIN) {
            LoginRoute(
                viewModel = viewModel(factory = LoginViewModel.factory(container)),
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
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
        composable(Routes.HOME) {
            val session by container.sessionStore.session.collectAsStateWithLifecycle()
            HomeScreen(
                clientName = session?.cliente?.nombre.orEmpty(),
                onLogout = {
                    container.sessionStore.clear()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
    }
}
