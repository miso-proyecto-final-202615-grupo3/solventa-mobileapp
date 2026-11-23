package com.solventa.app.data.push

import com.solventa.app.core.biometric.testSession
import com.solventa.app.data.session.InMemorySessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrarTest {
    private class FakePush : PushRepository {
        val results = ArrayDeque<PushResult>()
        val registered = mutableListOf<Triple<String, String, String>>()
        val unregistered = mutableListOf<Pair<String, String>>()
        override suspend fun register(accessToken: String, installationId: String, token: String): PushResult {
            registered += Triple(accessToken, installationId, token)
            return results.removeFirstOrNull() ?: PushResult.Registered
        }
        override suspend fun unregister(accessToken: String, installationId: String): PushResult {
            unregistered += accessToken to installationId
            return PushResult.Registered
        }
    }

    private val repo = FakePush()
    private val sessions = InMemorySessionStore()

    private fun TestScope.registrar(firebaseToken: String? = "fcm-1") =
        PushRegistrar(repo, sessions, { "inst-1" }, this, { firebaseToken })

    @Test
    fun `sin sesion o sin telefono vinculado no registra`() = runTest {
        val registrar = registrar()
        registrar.register()
        sessions.save(testSession(linked = false))
        registrar.register()
        advanceUntilIdle()

        assertTrue(repo.registered.isEmpty())
    }

    @Test
    fun `registra el token de firebase de esta instalacion y no lo repite en la misma sesion`() = runTest {
        sessions.save(testSession())
        val registrar = registrar()

        registrar.register()
        registrar.register()
        registrar.register("fcm-nuevo") // onNewToken
        advanceUntilIdle()

        assertEquals(listOf(Triple("a", "inst-1", "fcm-1"), Triple("a", "inst-1", "fcm-nuevo")), repo.registered)
    }

    @Test
    fun `sin firebase configurado no registra nada`() = runTest {
        sessions.save(testSession())

        registrar(firebaseToken = null).register()
        advanceUntilIdle()

        assertTrue(repo.registered.isEmpty())
    }

    @Test
    fun `reintenta por red y no reintenta si el telefono no esta vinculado`() = runTest {
        sessions.save(testSession())
        repo.results += listOf(PushResult.NetworkError, PushResult.ServiceUnavailable, PushResult.Registered)
        val registrar = registrar()

        registrar.register()
        advanceUntilIdle()
        assertEquals(3, repo.registered.size)

        repo.results += PushResult.DeviceNotLinked
        registrar.register("otro")
        advanceUntilIdle()
        assertEquals(4, repo.registered.size)
    }

    @Test
    fun `al cerrar sesion borra el token y permite registrarlo de nuevo`() = runTest {
        sessions.save(testSession())
        val registrar = registrar()
        registrar.register()
        advanceUntilIdle()

        registrar.unregister()
        registrar.register()
        advanceUntilIdle()

        assertEquals(listOf("a" to "inst-1"), repo.unregistered)
        assertEquals(2, repo.registered.size)
    }
}
