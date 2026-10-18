package com.solventa.app.data.auth

// Modelos de la sesión en la app. El login real viene del BFF Móvil (RemoteAuthRepository);
// id, nombre y estado KYC quedan pendientes de una consulta del cliente en el MS Identidad.

enum class EstadoKyc { PENDIENTE, VERIFICADO, RECHAZADO }

data class ClienteResumen(
    val id: String,
    /** Vacío cuando el backend aún no lo entrega. */
    val nombre: String,
    val estadoKyc: EstadoKyc,
    /** Indica si este dispositivo ya fue vinculado tras la prueba de vida (HU3.1.1). */
    val dispositivoVinculado: Boolean,
    val correo: String? = null,
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
    /** No se pudo llegar al servidor: sin conexión, servidor apagado o tiempo de espera agotado. */
    data object NetworkError : LoginResult

    /** El servidor respondió pero no pudo autenticar (5xx, respuesta inesperada o mal formada). */
    data object ServiceUnavailable : LoginResult
}
