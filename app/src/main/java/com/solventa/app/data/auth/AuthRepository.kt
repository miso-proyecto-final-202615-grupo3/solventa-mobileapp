package com.solventa.app.data.auth

interface AuthRepository {
    /** Equivale a `POST /v1/mobile/auth/login` en el BFF Móvil (contrato por confirmar). */
    suspend fun login(email: String, password: String, installationId: String? = null): LoginResult

    /** Renueva access/id tokens antes de reabrir una sesión protegida por biometría. */
    suspend fun refresh(session: Session): SessionRefreshResult
}
