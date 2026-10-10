package com.solventa.app.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockAuthRepositoryTest {

    private val repository = MockAuthRepository(latencyMillis = 0)

    @Test
    fun `cuenta del prototipo inicia sesion sin importar mayusculas ni espacios`() = runTest {
        val result = repository.login("  Camila.Rojas@correo.com ", "Solventa2026")

        assertTrue(result is LoginResult.Success)
        assertEquals("Camila", (result as LoginResult.Success).session.cliente.nombre)
    }

    @Test
    fun `contrasena incorrecta retorna credenciales invalidas`() = runTest {
        assertEquals(LoginResult.InvalidCredentials, repository.login("camila.rojas@correo.com", "x"))
    }

    @Test
    fun `correo desconocido retorna cuenta no encontrada`() = runTest {
        assertEquals(LoginResult.AccountNotFound, repository.login("otra@correo.com", "x"))
    }

    @Test
    fun `correo especial simula falla de red`() = runTest {
        assertEquals(
            LoginResult.NetworkError,
            repository.login(MockAuthRepository.SIMULATE_NETWORK_ERROR_EMAIL, "x"),
        )
    }
}
