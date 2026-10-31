package com.solventa.app.data.activation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
internal data class StartActivationRequestDto(
    @SerialName("installation_id") val installationId: String,
    val plataforma: String,
    val modelo: String,
    @SerialName("version_so") val osVersion: String,
)

@Serializable
internal data class CaptureChallengeDto(
    @SerialName("gesto") val gesture: String,
    val nonce: String,
)

@Serializable
internal data class CaptureDto(
    @SerialName("tipo") val type: String,
    @SerialName("reto") val challenge: CaptureChallengeDto? = null,
)

@Serializable
internal data class StartActivationResponseDto(
    @SerialName("dispositivo_vinculado") val deviceLinked: Boolean,
    @SerialName("sesion_id") val sessionId: String? = null,
    @SerialName("expira_en") val expiresAt: String? = null,
    val captura: CaptureDto? = null,
    @SerialName("intentos_restantes") val remainingAttempts: Int? = null,
)

class RemoteActivationRepository(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ActivationRepository {
    private val baseUrl = baseUrl.trimEnd('/')
    private val startUrl = this.baseUrl + START_PATH

    override suspend fun start(
        accessToken: String,
        installationId: String,
        platform: String,
        model: String,
        osVersion: String,
    ): ActivationStartResult = withContext(ioDispatcher) {
        val requestBody = json.encodeToString(
            StartActivationRequestDto.serializer(),
            StartActivationRequestDto(installationId, platform, model, osVersion),
        ).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(startUrl).header("Authorization", "Bearer $accessToken").post(requestBody).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ActivationStartResult.ServiceUnavailable
                val dto = json.decodeFromString(
                    StartActivationResponseDto.serializer(),
                    response.body?.string().orEmpty(),
                )
                if (dto.deviceLinked) return@use ActivationStartResult.DeviceAlreadyLinked
                val capture = dto.captura
                val challenge = capture?.challenge
                if (dto.sessionId.isNullOrBlank() || dto.expiresAt.isNullOrBlank() ||
                    capture?.type != "FRAMES" || challenge == null
                ) {
                    return@use ActivationStartResult.ServiceUnavailable
                }
                ActivationStartResult.ChallengeReady(
                    LivenessChallenge(
                        sessionId = dto.sessionId,
                        expiresAt = dto.expiresAt,
                        gesture = challenge.gesture,
                        nonce = challenge.nonce,
                        remainingAttempts = dto.remainingAttempts ?: 0,
                    ),
                )
            }
        } catch (_: SerializationException) {
            ActivationStartResult.ServiceUnavailable
        } catch (_: IOException) {
            ActivationStartResult.NetworkError
        } catch (_: IllegalArgumentException) {
            ActivationStartResult.ServiceUnavailable
        }
    }

    override suspend fun verify(accessToken: String, challenge: LivenessChallenge, frames: List<ByteArray>): ActivationVerificationResult = withContext(ioDispatcher) {
        if (frames.size != 3) return@withContext ActivationVerificationResult.ServiceUnavailable
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("nonce", challenge.nonce)
        listOf("frame_inicio", "frame_gesto", "frame_fin").zip(frames).forEach { (name, bytes) ->
            body.addFormDataPart(name, "$name.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        }
        try {
            client.newCall(Request.Builder().url(baseUrl + VERIFY_PATH.format(challenge.sessionId)).header("Authorization", "Bearer $accessToken").post(body.build()).build()).execute().use { response ->
                if (!response.isSuccessful) return@use ActivationVerificationResult.ServiceUnavailable
                val data = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                if (data["resultado"]?.toString()?.trim('"') == "APROBADO") ActivationVerificationResult.Approved
                else ActivationVerificationResult.Rejected(data["motivo"]?.toString()?.trim('"'))
            }
        } catch (_: IOException) { ActivationVerificationResult.NetworkError }
        catch (_: Exception) { ActivationVerificationResult.ServiceUnavailable }
    }

    companion object {
        const val START_PATH = "/api/v1/bff-mobile/activacion/sesiones"
        const val VERIFY_PATH = "/api/v1/bff-mobile/activacion/sesiones/%s/verificacion"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
