package com.solventa.app.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockAuthRepositoryTest {

    private val repository = MockAuthRepository(latencyMillis = 0)

    @Test
    fun `cuenta del prototipo inicia sesion sin importar mayusculas ni espacios`() = runTest {
        val result = repository.login("  Camila.Rojas@correo.com ", "123456")

        assertTrue(result is LoginResult.Success)
        val cliente = (result as LoginResult.Success).session.cliente
        assertEquals("Camila", cliente.nombre)
        assertTrue(cliente.dispositivoVinculado)
    }

    @Test
    fun `cuenta sin telefono activado retorna dispositivo no vinculado`() = runTest {
        val result = repository.login("andres.perez@correo.com", "123456")

        assertFalse((result as LoginResult.Success).session.cliente.dispositivoVinculado)
    }

    @Test
    fun `cualquier correo valido con contrasena de 6 o mas caracteres inicia sesion`() = runTest {
        val result = repository.login("laura.gomez@correo.com", "abcdef")

        assertTrue(result is LoginResult.Success)
        val cliente = (result as LoginResult.Success).session.cliente
        assertEquals("Laura", cliente.nombre)
        assertTrue(cliente.dispositivoVinculado)
    }

    @Test
    fun `contrasena de menos de 6 caracteres retorna credenciales invalidas`() = runTest {
        assertEquals(
            LoginResult.InvalidCredentials,
            repository.login("camila.rojas@correo.com", "12345"),
        )
        assertEquals(
            LoginResult.InvalidCredentials,
            repository.login("otra@correo.com", "x"),
        )
    }

    @Test
    fun `correo con formato invalido retorna cuenta no encontrada`() = runTest {
        assertEquals(LoginResult.AccountNotFound, repository.login("sin-arroba", "123456"))
    }

    @Test
    fun `correo especial simula cuenta no encontrada`() = runTest {
        assertEquals(
            LoginResult.AccountNotFound,
            repository.login(MockAuthRepository.SIMULATE_ACCOUNT_NOT_FOUND_EMAIL, "123456"),
        )
    }

    @Test
    fun `correo especial simula falla de red`() = runTest {
        assertEquals(
            LoginResult.NetworkError,
            repository.login(MockAuthRepository.SIMULATE_NETWORK_ERROR_EMAIL, "123456"),
        )
    }

    @Test
    fun `correo especial simula servicio no disponible`() = runTest {
        assertEquals(
            LoginResult.ServiceUnavailable,
            repository.login(MockAuthRepository.SIMULATE_SERVICE_UNAVAILABLE_EMAIL, "123456"),
        )
    }
}
