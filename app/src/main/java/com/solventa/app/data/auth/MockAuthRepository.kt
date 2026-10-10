package com.solventa.app.data.auth

import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Backend simulado mientras el BFF Móvil no está disponible.
 * Las cuentas son ficticias y coinciden con las del prototipo navegable.
 */
class MockAuthRepository(
    private val latencyMillis: Long = 600,
) : AuthRepository {

    private data class MockAccount(val password: String, val cliente: ClienteResumen)

    private val accounts = mapOf(
        "camila.rojas@correo.com" to MockAccount(
            password = "Solventa2026",
            cliente = ClienteResumen(
                id = "c-0001",
                nombre = "Camila",
                // Simula que ya completó la prueba de vida (HU3.1.1) y vinculó el teléfono.
                estadoKyc = EstadoKyc.VERIFICADO,
                dispositivoVinculado = true,
            ),
        ),
        // Cliente que aún no activa el teléfono (HU3.1.1 pendiente): no se le ofrece biometría.
        "andres.perez@correo.com" to MockAccount(
            password = "Solventa2026",
            cliente = ClienteResumen(
                id = "c-0002",
                nombre = "Andrés",
                estadoKyc = EstadoKyc.PENDIENTE,
                dispositivoVinculado = false,
            ),
        ),
    )

    override suspend fun login(email: String, password: String): LoginResult {
        delay(latencyMillis)
        val normalized = email.trim().lowercase()
        if (normalized == SIMULATE_NETWORK_ERROR_EMAIL) return LoginResult.NetworkError
        val account = accounts[normalized] ?: return LoginResult.AccountNotFound
        if (account.password != password) return LoginResult.InvalidCredentials
        return LoginResult.Success(
            Session(
                accessToken = "mock-access-${UUID.randomUUID()}",
                refreshToken = "mock-refresh-${UUID.randomUUID()}",
                expiresInSeconds = 3600,
                cliente = account.cliente,
            ),
        )
    }

    companion object {
        /** Correo que simula una falla de red para probar ese estado en la UI. */
        const val SIMULATE_NETWORK_ERROR_EMAIL = "sin.red@correo.com"
    }
}
