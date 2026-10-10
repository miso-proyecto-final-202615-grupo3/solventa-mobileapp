package com.solventa.app.core.biometric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricLockoutPolicyTest {

    private val settings = InMemoryBiometricSettings()
    private val clock = FakeClock()
    private val policy = BiometricLockoutPolicy(settings, clock)

    @Test
    fun `los dos primeros fallos permiten reintentar con contador`() {
        assertEquals(FailureOutcome.Retry(1, 3), policy.registerFailure())
        assertEquals(FailureOutcome.Retry(2, 3), policy.registerFailure())
        assertFalse(policy.isLockedOut())
    }

    @Test
    fun `el tercer fallo consecutivo bloquea 5 minutos`() {
        repeat(2) { policy.registerFailure() }

        assertEquals(FailureOutcome.LockedOut, policy.registerFailure())

        assertTrue(policy.isLockedOut())
        assertEquals(BiometricLockoutPolicy.LOCKOUT_MILLIS, policy.remainingLockoutMillis())
    }

    @Test
    fun `el bloqueo expira a los 5 minutos`() {
        repeat(3) { policy.registerFailure() }

        clock.now += BiometricLockoutPolicy.LOCKOUT_MILLIS - 1
        assertTrue(policy.isLockedOut())
        clock.now += 1
        assertFalse(policy.isLockedOut())
    }

    @Test
    fun `el bloqueo persiste en la configuracion aunque se recree la politica`() {
        repeat(3) { policy.registerFailure() }

        val afterRestart = BiometricLockoutPolicy(settings, clock)

        assertTrue(afterRestart.isLockedOut())
    }

    @Test
    fun `el contador persiste entre reinicios de la app`() {
        policy.registerFailure()
        policy.registerFailure()

        val afterRestart = BiometricLockoutPolicy(settings, clock)

        assertEquals(FailureOutcome.LockedOut, afterRestart.registerFailure())
    }

    @Test
    fun `un acceso exitoso reinicia el contador`() {
        policy.registerFailure()
        policy.registerFailure()

        policy.registerSuccess()

        assertEquals(FailureOutcome.Retry(1, 3), policy.registerFailure())
    }

    @Test
    fun `tras expirar el bloqueo el contador empieza de cero`() {
        repeat(3) { policy.registerFailure() }
        clock.now += BiometricLockoutPolicy.LOCKOUT_MILLIS

        assertEquals(FailureOutcome.Retry(1, 3), policy.registerFailure())
    }

    @Test
    fun `el bloqueo del sistema operativo tambien bloquea 5 minutos`() {
        policy.registerSystemLockout()

        assertTrue(policy.isLockedOut())
        assertEquals(BiometricLockoutPolicy.LOCKOUT_MILLIS, policy.remainingLockoutMillis())
    }
}
