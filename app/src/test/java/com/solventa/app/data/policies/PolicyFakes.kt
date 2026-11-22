package com.solventa.app.data.policies

import com.solventa.app.core.crypto.SecretKeyLocalCipher
import com.solventa.app.data.policies.local.CertificateStore
import com.solventa.app.data.policies.local.WalletStore
import com.solventa.app.data.policies.sync.ConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.time.Instant
import javax.crypto.KeyGenerator

fun testCipher() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().let { key -> SecretKeyLocalCipher { key } }

fun testPolicy(
    id: String = "8d2b6c1e-4a7f-4f0e-9a51-0c6b1d9e2f10",
    status: String = "EMITIDA",
    number: String = "SLV-2026-08341",
) = Policy(
    id = id,
    number = number,
    status = status,
    branch = "VIDA",
    productCode = "SEGURO_VIDA_HIPOTECARIO",
    productName = "Seguro de vida hipotecario",
    validFrom = Instant.parse("2026-03-12T00:00:00Z"),
    validTo = Instant.parse("2027-03-12T00:00:00Z"),
    issuedAt = Instant.parse("2026-03-12T15:20:00Z"),
    totalPremium = "120000.00",
    coverages = listOf(
        Coverage("c1", "Muerte por cualquier causa", "200000000.00", "0.00"),
        Coverage("c2", null, "50000000.00", "0.00"),
    ),
)

class FakeConnectivity(online: Boolean = true) : ConnectivityMonitor {
    private val _online = MutableStateFlow(online)
    override val online: StateFlow<Boolean> = _online
    fun set(value: Boolean) {
        _online.value = value
    }
}

class FakePolicyRepository : PolicyRepository {
    val wallets = ArrayDeque<WalletFetchResult>()
    val certificates = mutableMapOf<String, CertificateResult>()
    val tokensUsed = mutableListOf<String>()
    var fetchCount = 0

    override suspend fun fetchWallet(accessToken: String, correlationId: String): WalletFetchResult {
        fetchCount++
        tokensUsed += accessToken
        return wallets.removeFirstOrNull() ?: WalletFetchResult.ServiceUnavailable
    }

    override suspend fun certificate(accessToken: String, policyId: String, correlationId: String): CertificateResult =
        certificates[policyId] ?: CertificateResult.NotAvailable
}

class MemoryWalletStore : WalletStore {
    var snapshot: WalletSnapshot? = null
    var failSave = false

    override fun load() = snapshot

    override fun save(snapshot: WalletSnapshot) {
        if (failSave) throw IOException("disco lleno")
        this.snapshot = snapshot
    }
}

class MemoryCertificateStore : CertificateStore {
    val files = mutableMapOf<String, ByteArray>()
    var clears = 0

    override fun has(policyId: String) = policyId in files
    override fun load(policyId: String) = files[policyId]
    override fun save(policyId: String, pdf: ByteArray) {
        files[policyId] = pdf
    }

    override fun retainOnly(policyIds: Set<String>) {
        files.keys.retainAll(policyIds)
    }

    override fun clear() {
        clears++
        files.clear()
    }
}
