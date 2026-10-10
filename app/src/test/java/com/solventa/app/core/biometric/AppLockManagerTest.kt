package com.solventa.app.core.biometric

import com.solventa.app.data.session.InMemorySessionStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockManagerTest {

    private val vault = FakeBiometricVault()
    private val sessionStore = InMemorySessionStore()
    private val clock = FakeClock(now = 0)
    private val manager = AppLockManager(vault, sessionStore, clock)

    private fun enableBiometricWithSession() {
        val session = testSession()
        vault.seal(javax.crypto.Cipher.getInstance("AES/GCM/NoPadding"), session)
        sessionStore.save(session)
    }

    @Test
    fun `al abrir la app con biometria activa queda bloqueada`() {
        enableBiometricWithSession()

        manager.onColdStart()

        assertTrue(manager.locked.value)
    }

    @Test
    fun `al abrir la app sin biometria no se bloquea`() {
        manager.onColdStart()

        assertFalse(manager.locked.value)
    }

    @Test
    fun `volver de segundo plano tras mas de 60 segundos bloquea`() {
        enableBiometricWithSession()

        manager.onAppBackgrounded()
        clock.now += 60_001
        manager.onAppForegrounded()

        assertTrue(manager.locked.value)
    }

    @Test
    fun `volver de segundo plano en 60 segundos o menos no bloquea`() {
        enableBiometricWithSession()

        manager.onAppBackgrounded()
        clock.now += 60_000
        manager.onAppForegrounded()

        assertFalse(manager.locked.value)
    }

    @Test
    fun `sin sesion activa no se bloquea al volver`() {
        enableBiometricWithSession()
        sessionStore.clear()

        manager.onAppBackgrounded()
        clock.now += 120_000
        manager.onAppForegrounded()

        assertFalse(manager.locked.value)
    }

    @Test
    fun `con biometria desactivada no se bloquea al volver`() {
        sessionStore.save(testSession())

        manager.onAppBackgrounded()
        clock.now += 120_000
        manager.onAppForegrounded()

        assertFalse(manager.locked.value)
    }

    @Test
    fun `unlock retira el bloqueo`() {
        enableBiometricWithSession()
        manager.onColdStart()

        manager.unlock()

        assertFalse(manager.locked.value)
    }
}
