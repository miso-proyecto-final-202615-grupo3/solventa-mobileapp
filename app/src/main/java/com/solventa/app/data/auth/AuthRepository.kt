package com.solventa.app.data.auth

interface AuthRepository {
    /** Equivale a `POST /v1/mobile/auth/login` en el BFF Móvil (contrato por confirmar). */
    suspend fun login(email: String, password: String): LoginResult
}
