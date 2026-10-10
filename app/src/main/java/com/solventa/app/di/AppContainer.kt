package com.solventa.app.di

import com.solventa.app.BuildConfig
import com.solventa.app.data.auth.AuthRepository
import com.solventa.app.data.auth.MockAuthRepository
import com.solventa.app.data.session.InMemorySessionStore
import com.solventa.app.data.session.SessionStore

/** Contenedor de dependencias manual; suficiente para el tamaño actual de la app. */
class AppContainer {
    val authRepository: AuthRepository =
        if (BuildConfig.USE_MOCK_BACKEND) {
            MockAuthRepository()
        } else {
            TODO("Implementación remota contra el BFF Móvil pendiente de contratos")
        }

    val sessionStore: SessionStore = InMemorySessionStore()
}
