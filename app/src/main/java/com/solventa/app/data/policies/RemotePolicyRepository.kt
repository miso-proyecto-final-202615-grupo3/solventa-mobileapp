package com.solventa.app.data.policies

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

@Serializable
internal data class WalletProductDto(val codigo: String, val nombre: String)

@Serializable
internal data class WalletCoverageDto(
    @SerialName("cobertura_id") val id: String,
    val nombre: String? = null,
    // Decimal en el contrato: puede llegar como texto o como número.
    @SerialName("limite_asegurado") val insuredLimit: JsonElement,
    val deducible: JsonElement,
)

@Serializable
internal data class WalletPolicyDto(
    @SerialName("poliza_id") val id: String,
    val numero: String,
    val estado: String,
    val ramo: String,
    val producto: WalletProductDto? = null,
    @SerialName("vigencia_desde") val validFrom: String,
    @SerialName("vigencia_hasta") val validTo: String,
    @SerialName("emitida_en") val issuedAt: String? = null,
    @SerialName("prima_total") val totalPremium: JsonElement,
    val coberturas: List<WalletCoverageDto>,
)

@Serializable
internal data class WalletResponseDto(val polizas: List<WalletPolicyDto>)

@Serializable
internal data class WalletErrorDto(val codigo: String? = null)

/**
 * Billetera contra el BFF Móvil (muestra del contrato: `solventa-backend/docs/contracts/polizas/billetera.json`).
 *
 * Nunca registra el token ni el contenido de las pólizas. [demoEmail] solo se usa en debug (modo demo del BFF sin JWT).
 */
class RemotePolicyRepository(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val demoEmail: String? = null,
) : PolicyRepository {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun fetchWallet(accessToken: String, correlationId: String): WalletFetchResult =
        withContext(ioDispatcher) {
            val request = authorized(baseUrl + POLICIES_PATH, accessToken, correlationId).get().build()
            try {
                client.newCall(request).execute().use(::parseWallet)
            } catch (_: IOException) {
                WalletFetchResult.NetworkError
            } catch (_: SerializationException) {
                WalletFetchResult.ServiceUnavailable
            } catch (_: IllegalArgumentException) {
                WalletFetchResult.ServiceUnavailable
            } catch (_: DateTimeParseException) {
                WalletFetchResult.ServiceUnavailable
            }
        }

    override suspend fun certificate(accessToken: String, policyId: String, correlationId: String): CertificateResult =
        withContext(ioDispatcher) {
            val request = authorized("$baseUrl$POLICIES_PATH/$policyId/certificado/descarga", accessToken, correlationId)
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            val bytes = response.body?.bytes() ?: ByteArray(0)
                            if (bytes.isEmpty() || bytes.size > MAX_CERTIFICATE_BYTES) {
                                CertificateResult.ServiceUnavailable
                            } else {
                                CertificateResult.Loaded(bytes)
                            }
                        }
                        response.code == 401 -> CertificateResult.SessionExpired
                        response.code == 404 || response.code == 409 -> CertificateResult.NotAvailable
                        else -> CertificateResult.ServiceUnavailable
                    }
                }
            } catch (_: IOException) {
                CertificateResult.NetworkError
            }
        }

    private fun authorized(url: String, accessToken: String, correlationId: String): Request.Builder =
        Request.Builder().url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("X-Correlation-Id", correlationId)
            .apply { demoEmail?.takeIf { it.isNotBlank() }?.let { header("X-Demo-Correo", it) } }

    private fun parseWallet(response: Response): WalletFetchResult {
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful) {
            val dto = json.decodeFromString(WalletResponseDto.serializer(), text)
            return WalletFetchResult.Loaded(dto.polizas.map(::toPolicy))
        }
        return when {
            response.code == 401 -> WalletFetchResult.SessionExpired
            response.code == 404 && errorCode(text) == "CLIENTE_NO_REGISTRADO" -> WalletFetchResult.ClientNotRegistered
            else -> WalletFetchResult.ServiceUnavailable
        }
    }

    private fun toPolicy(dto: WalletPolicyDto) = Policy(
        id = dto.id,
        number = dto.numero,
        status = dto.estado,
        branch = dto.ramo,
        productCode = dto.producto?.codigo,
        productName = dto.producto?.nombre,
        validFrom = Instant.parse(dto.validFrom),
        validTo = Instant.parse(dto.validTo),
        issuedAt = dto.issuedAt?.let(Instant::parse),
        totalPremium = dto.totalPremium.asText(),
        coverages = dto.coberturas.map {
            Coverage(it.id, it.nombre, it.insuredLimit.asText(), it.deducible.asText())
        },
    )

    private fun JsonElement.asText(): String = jsonPrimitive.content

    private fun errorCode(text: String): String? = try {
        json.decodeFromString(WalletErrorDto.serializer(), text).codigo
    } catch (_: SerializationException) {
        null
    }

    companion object {
        private const val POLICIES_PATH = "/api/v1/bff-mobile/polizas"
        private const val MAX_CERTIFICATE_BYTES = 10 * 1024 * 1024
        private val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
