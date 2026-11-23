package com.solventa.app.data.policies

import java.time.Instant

/** Billetera de pólizas (HU3.2.1 y HU3.2.2). Los importes viajan como texto para no perder precisión. */
data class Coverage(
    val id: String,
    /** `null` si el catálogo no la conoce; la UI muestra un texto genérico. */
    val name: String?,
    val insuredLimit: String,
    val deductible: String,
)

data class Policy(
    val id: String,
    val number: String,
    /** Estado tal como lo informa el backend (EMITIDA, VIGENTE, CANCELADA…); se muestra, no se interpreta. */
    val status: String,
    val branch: String,
    val productCode: String?,
    val productName: String?,
    val validFrom: Instant,
    val validTo: Instant,
    val issuedAt: Instant?,
    val totalPremium: String,
    val coverages: List<Coverage>,
)

/** Copia local de las pólizas de un cliente y el momento de la última sincronización exitosa. */
data class WalletSnapshot(
    val clientId: String,
    val policies: List<Policy>,
    val syncedAt: Instant,
)

sealed interface WalletFetchResult {
    data class Loaded(val policies: List<Policy>) : WalletFetchResult
    data object SessionExpired : WalletFetchResult
    data object ClientNotRegistered : WalletFetchResult
    data object NetworkError : WalletFetchResult
    data object ServiceUnavailable : WalletFetchResult
}

sealed interface CertificateResult {
    data class Loaded(val pdf: ByteArray) : CertificateResult {
        override fun equals(other: Any?) = other is Loaded && pdf.contentEquals(other.pdf)
        override fun hashCode() = pdf.contentHashCode()
    }

    /** El certificado aún no se generó o la póliza no lo tiene. */
    data object NotAvailable : CertificateResult
    data object SessionExpired : CertificateResult
    data object NetworkError : CertificateResult
    data object ServiceUnavailable : CertificateResult
}

interface PolicyRepository {
    /** `GET /polizas` del BFF Móvil: todas las pólizas del cliente del token. */
    suspend fun fetchWallet(accessToken: String, correlationId: String): WalletFetchResult

    /** `GET /polizas/{id}/certificado/descarga`: PDF del certificado / credencial digital. */
    suspend fun certificate(accessToken: String, policyId: String, correlationId: String): CertificateResult
}
