package com.solventa.app.data.auth

// Modelos BORRADOR del contrato de autenticación con el BFF Móvil.
// Pendiente de confirmar con el equipo de backend (ver docs/contratos-pendientes.md).

enum class EstadoKyc { PENDIENTE, VERIFICADO, RECHAZADO }

data class ClienteResumen(
    val id: String,
    val nombre: String,
    val estadoKyc: EstadoKyc,
    /** Indica si este dispositivo ya fue vinculado tras la prueba de vida (HU3.1.1). */
    val dispositivoVinculado: Boolean,
)

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val cliente: ClienteResumen,
)

sealed interface LoginResult {
    data class Success(val session: Session) : LoginResult
    data object InvalidCredentials : LoginResult
    data object AccountNotFound : LoginResult
    data object NetworkError : LoginResult
}
