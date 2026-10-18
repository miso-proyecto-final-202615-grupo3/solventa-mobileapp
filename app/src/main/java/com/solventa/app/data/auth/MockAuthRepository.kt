package com.solventa.app.data.auth

import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Backend simulado mientras el BFF Móvil no está disponible.
 *
 * Regla de demostración: cualquier correo con formato válido y una contraseña de
 * [MIN_PASSWORD_LENGTH] o más caracteres inicia sesión. Las cuentas de [accounts] conservan su
 * perfil (para poder mostrar el flujo de activación del teléfono); cualquier otro correo entra
 * como cliente verificado con el teléfono ya vinculado.
 */
class MockAuthRepository(
    private val latencyMillis: Long = 600,
) : AuthRepository {

    private val accounts = mapOf(
        "camila.rojas@correo.com" to ClienteResumen(
            id = "c-0001",
            nombre = "Camila",
            // Simula que ya completó la prueba de vida (HU3.1.1) y vinculó el teléfono.
            estadoKyc = EstadoKyc.VERIFICADO,
            dispositivoVinculado = true,
        ),
        // Cliente que aún no activa el teléfono (HU3.1.1 pendiente): no se le ofrece biometría.
        "andres.perez@correo.com" to ClienteResumen(
            id = "c-0002",
            nombre = "Andrés",
            estadoKyc = EstadoKyc.PENDIENTE,
            dispositivoVinculado = false,
        ),
    )

    override suspend fun login(email: String, password: String): LoginResult {
        delay(latencyMillis)
        val normalized = email.trim().lowercase()
        if (normalized == SIMULATE_NETWORK_ERROR_EMAIL) return LoginResult.NetworkError
        if (normalized == SIMULATE_SERVICE_UNAVAILABLE_EMAIL) return LoginResult.ServiceUnavailable
        if (normalized == SIMULATE_ACCOUNT_NOT_FOUND_EMAIL || !EMAIL_FORMAT.matches(normalized)) {
            return LoginResult.AccountNotFound
        }
        if (password.length < MIN_PASSWORD_LENGTH) return LoginResult.InvalidCredentials
        return LoginResult.Success(
            Session(
                accessToken = "mock-access-${UUID.randomUUID()}",
                refreshToken = "mock-refresh-${UUID.randomUUID()}",
                expiresInSeconds = 3600,
                cliente = accounts[normalized] ?: demoCliente(normalized),
            ),
        )
    }

    private fun demoCliente(email: String) = ClienteResumen(
        id = "c-demo",
        nombre = email.substringBefore('@').substringBefore('.').replaceFirstChar { it.uppercase() },
        estadoKyc = EstadoKyc.VERIFICADO,
        dispositivoVinculado = true,
    )

    companion object {
        /** Correo que simula una falla de red para probar ese estado en la UI. */
        const val SIMULATE_NETWORK_ERROR_EMAIL = "sin.red@correo.com"

        /** Correo que simula el servidor sin poder autenticar (p. ej. 503) para probar ese estado en la UI. */
        const val SIMULATE_SERVICE_UNAVAILABLE_EMAIL = "sin.servicio@correo.com"

        /** Correo que simula "cuenta no encontrada" para probar ese estado en la UI. */
        const val SIMULATE_ACCOUNT_NOT_FOUND_EMAIL = "sin.cuenta@correo.com"

        /** Largo mínimo de la contraseña para la demostración. */
        const val MIN_PASSWORD_LENGTH = 6

        private val EMAIL_FORMAT = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
