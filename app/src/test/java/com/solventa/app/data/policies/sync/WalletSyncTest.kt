package com.solventa.app.data.policies.sync

import com.solventa.app.core.biometric.testSession
import com.solventa.app.data.auth.AuthRepository
import com.solventa.app.data.auth.LoginResult
import com.solventa.app.data.auth.Session
import com.solventa.app.data.auth.SessionRefreshResult
import com.solventa.app.data.policies.CertificateResult
import com.solventa.app.data.policies.FakeConnectivity
import com.solventa.app.data.policies.FakePolicyRepository
import com.solventa.app.data.policies.MemoryCertificateStore
import com.solventa.app.data.policies.MemoryWalletStore
import com.solventa.app.data.policies.PolicyRepository
import com.solventa.app.data.policies.WalletFetchResult
import com.solventa.app.data.policies.WalletSnapshot
import com.solventa.app.data.policies.testPolicy
import com.solventa.app.data.session.InMemorySessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class WalletSyncTest {
    private val repository = FakePolicyRepository()
    private val store = MemoryWalletStore()
    private val certificates = MemoryCertificateStore()
    private val sessionStore = InMemorySessionStore()
    private val connectivity = FakeConnectivity(online = true)
    private val auth = FakeAuth()
    private val now = Instant.parse("2026-11-18T14:05:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val policyA = testPolicy("aaaaaaaa-0000-4000-8000-000000000001", number = "SLV-A")
    private val policyB = testPolicy("aaaaaaaa-0000-4000-8000-000000000002", number = "SLV-B")

    private class FakeAuth : AuthRepository {
        var refresh: SessionRefreshResult = SessionRefreshResult.ReauthenticationRequired
        var refreshCount = 0
        override suspend fun login(email: String, password: String, installationId: String?): LoginResult =
            error("no se usa")

        override suspend fun refresh(session: Session): SessionRefreshResult {
            refreshCount++
            return refresh
        }
    }

    private fun TestScope.sync(repository: PolicyRepository = this@WalletSyncTest.repository) = WalletSync(
        repository = repository,
        store = store,
        certificates = certificates,
        sessionStore = sessionStore,
        authRepository = auth,
        connectivity = connectivity,
        scope = backgroundScope,
        clock = clock,
        retryDelaysMillis = listOf(1_000L, 2_000L),
        newCorrelationId = { "corr" },
    ).also { it.start() }

    private fun loaded(vararg policies: com.solventa.app.data.policies.Policy) = WalletFetchResult.Loaded(policies.toList())

    @Test
    fun `con sesion y conexion sincroniza y guarda la copia con certificados`() = runTest {
        sessionStore.save(testSession())
        repository.wallets += loaded(policyA)
        repository.certificates[policyA.id] = CertificateResult.Loaded("%PDF-A".toByteArray())

        val sync = sync()
        runCurrent()

        assertEquals(WalletSnapshot("c-1", listOf(policyA), now), sync.snapshot.value)
        assertEquals(sync.snapshot.value, store.snapshot)
        assertArrayEquals("%PDF-A".toByteArray(), certificates.files[policyA.id])
        assertEquals(SyncState.IDLE, sync.state.value)
        assertEquals(1, sync.certificatesVersion.value)
    }

    @Test
    fun `sin conexion muestra la copia guardada y no consulta al servidor`() = runTest {
        connectivity.set(false)
        store.snapshot = WalletSnapshot("c-1", listOf(policyA), now.minusSeconds(3600))
        sessionStore.save(testSession())

        val sync = sync()
        runCurrent()

        assertEquals(listOf(policyA), sync.snapshot.value?.policies)
        assertEquals(now.minusSeconds(3600), sync.snapshot.value?.syncedAt)
        assertEquals(0, repository.fetchCount)
        assertFalse(sync.online.value)
    }

    @Test
    fun `al recuperar la conexion sincroniza y reemplaza lo local por lo del servidor`() = runTest {
        connectivity.set(false)
        store.snapshot = WalletSnapshot("c-1", listOf(policyA, policyB), now.minusSeconds(3600))
        certificates.files[policyA.id] = "vieja-A".toByteArray()
        certificates.files[policyB.id] = "vieja-B".toByteArray()
        sessionStore.save(testSession())
        // En el servidor: A fue cancelada y B ya no aparece.
        repository.wallets += loaded(testPolicy(policyA.id, status = "CANCELADA", number = "SLV-A"))
        val sync = sync()
        runCurrent()

        connectivity.set(true)
        runCurrent()

        val policies = sync.snapshot.value!!.policies
        assertEquals(listOf(policyA.id), policies.map { it.id })
        assertEquals("CANCELADA", policies.single().status)
        assertEquals(now, sync.snapshot.value!!.syncedAt)
        assertEquals(setOf(policyA.id), certificates.files.keys)
    }

    @Test
    fun `mientras sincroniza la copia local sigue disponible`() = runTest {
        store.snapshot = WalletSnapshot("c-1", listOf(policyA), now.minusSeconds(3600))
        sessionStore.save(testSession())
        val gate = CompletableDeferred<WalletFetchResult>()
        val blocking = object : PolicyRepository {
            override suspend fun fetchWallet(accessToken: String, correlationId: String) = gate.await()
            override suspend fun certificate(accessToken: String, policyId: String, correlationId: String) =
                CertificateResult.NotAvailable
        }

        val sync = sync(blocking)
        runCurrent()

        assertEquals(SyncState.SYNCING, sync.state.value)
        assertEquals(listOf(policyA), sync.snapshot.value?.policies)

        gate.complete(loaded(policyB))
        runCurrent()

        assertEquals(SyncState.IDLE, sync.state.value)
        assertEquals(listOf(policyB), sync.snapshot.value?.policies)
    }

    @Test
    fun `si falla no toca la copia ni los certificados y reintenta`() = runTest {
        val anterior = WalletSnapshot("c-1", listOf(policyA), now.minusSeconds(3600))
        store.snapshot = anterior
        certificates.files[policyA.id] = "guardado".toByteArray()
        sessionStore.save(testSession())
        repository.wallets += WalletFetchResult.NetworkError
        repository.wallets += loaded(policyB)

        val sync = sync()
        runCurrent()

        assertEquals(SyncState.FAILED, sync.state.value)
        assertEquals(anterior, sync.snapshot.value)
        assertEquals(anterior, store.snapshot)
        assertArrayEquals("guardado".toByteArray(), certificates.files[policyA.id])

        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(SyncState.IDLE, sync.state.value)
        assertEquals(listOf(policyB), sync.snapshot.value?.policies)
        assertEquals(2, repository.fetchCount)
    }

    @Test
    fun `los reintentos se agotan y se detienen`() = runTest {
        sessionStore.save(testSession())

        val sync = sync()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(SyncState.FAILED, sync.state.value)
        assertEquals(3, repository.fetchCount) // intento inicial + 2 reintentos
    }

    @Test
    fun `no reintenta sin conexion`() = runTest {
        sessionStore.save(testSession())
        val sync = sync()
        runCurrent()
        connectivity.set(false)
        runCurrent()

        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(1, repository.fetchCount)
        assertEquals(SyncState.FAILED, sync.state.value)
    }

    @Test
    fun `si no se puede guardar en disco queda la copia anterior`() = runTest {
        val anterior = WalletSnapshot("c-1", listOf(policyA), now.minusSeconds(3600))
        store.snapshot = anterior
        store.failSave = true
        sessionStore.save(testSession())
        repository.wallets += loaded(policyB)

        val sync = sync()
        runCurrent()

        assertEquals(anterior, sync.snapshot.value)
        assertEquals(SyncState.FAILED, sync.state.value)
    }

    @Test
    fun `sesion vencida renueva una vez y reintenta con el token nuevo`() = runTest {
        sessionStore.save(testSession())
        auth.refresh = SessionRefreshResult.Success(testSession().copy(accessToken = "a2"))
        repository.wallets += WalletFetchResult.SessionExpired
        repository.wallets += loaded(policyA)

        val sync = sync()
        runCurrent()

        assertEquals(listOf("a", "a2"), repository.tokensUsed)
        assertEquals(1, auth.refreshCount)
        assertEquals("a2", sessionStore.session.value?.accessToken)
        assertEquals(listOf(policyA), sync.snapshot.value?.policies)
    }

    @Test
    fun `si no se puede renovar la sesion no entra en bucle y conserva la copia`() = runTest {
        val anterior = WalletSnapshot("c-1", listOf(policyA), now.minusSeconds(3600))
        store.snapshot = anterior
        sessionStore.save(testSession())
        repository.wallets += WalletFetchResult.SessionExpired

        val sync = sync()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(1, repository.fetchCount)
        assertEquals(1, auth.refreshCount)
        assertEquals(anterior, sync.snapshot.value)
        assertEquals(SyncState.FAILED, sync.state.value)
    }

    @Test
    fun `la copia de otro cliente no se muestra y sus certificados se borran`() = runTest {
        store.snapshot = WalletSnapshot("otro-cliente", listOf(policyA), now.minusSeconds(3600))
        certificates.files[policyA.id] = "del otro".toByteArray()
        connectivity.set(false)
        sessionStore.save(testSession())

        val sync = sync()
        runCurrent()

        assertNull(sync.snapshot.value)

        repository.wallets += loaded(policyB)
        connectivity.set(true)
        runCurrent()

        assertEquals("c-1", sync.snapshot.value?.clientId)
        assertEquals(1, certificates.clears)
        assertFalse(policyA.id in certificates.files)
    }

    @Test
    fun `al cerrar sesion la billetera deja de mostrarse`() = runTest {
        sessionStore.save(testSession())
        repository.wallets += loaded(policyA)
        val sync = sync()
        runCurrent()
        assertTrue(sync.snapshot.value != null)

        sessionStore.clear()
        runCurrent()

        assertNull(sync.snapshot.value)
        assertEquals(SyncState.IDLE, sync.state.value)
    }

    @Test
    fun `un certificado que no baja no invalida la sincronizacion`() = runTest {
        sessionStore.save(testSession())
        repository.wallets += loaded(policyA, policyB)
        repository.certificates[policyA.id] = CertificateResult.NetworkError
        repository.certificates[policyB.id] = CertificateResult.Loaded("b".toByteArray())

        val sync = sync()
        runCurrent()

        assertEquals(listOf(policyA, policyB), sync.snapshot.value?.policies)
        assertEquals(SyncState.IDLE, sync.state.value)
        // La falla de red corta la descarga del resto; se reintenta en la próxima sincronización.
        assertFalse(policyB.id in certificates.files)
    }

    @Test
    fun `cliente no registrado no es un fallo reintentable`() = runTest {
        sessionStore.save(testSession())
        repository.wallets += WalletFetchResult.ClientNotRegistered

        val sync = sync()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(1, repository.fetchCount)
        assertEquals(SyncState.IDLE, sync.state.value)
    }
}
