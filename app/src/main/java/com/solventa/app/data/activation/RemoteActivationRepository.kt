package com.solventa.app.data.activation

import com.solventa.app.core.camera.CapturedFrames
import com.solventa.app.core.device.DeviceInfo
import com.solventa.app.core.liveness.FrameSlot
import com.solventa.app.core.liveness.LivenessChallenge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
internal data class CaptureChallengeDto(val gesto: String, val nonce: String)

@Serializable
internal data class CaptureSdkDto(val nombre: String, @SerialName("session_token") val sessionToken: String)

@Serializable
internal data class CaptureDto(
    val tipo: String,
    val reto: CaptureChallengeDto? = null,
    val sdk: CaptureSdkDto? = null,
)

@Serializable
internal data class StartActivationResponseDto(
    @SerialName("dispositivo_vinculado") val deviceLinked: Boolean = false,
    @SerialName("sesion_id") val sessionId: String? = null,
    @SerialName("expira_en") val expiresAt: String? = null,
    val captura: CaptureDto? = null,
    @SerialName("intentos_restantes") val remainingAttempts: Int? = null,
)

@Serializable
internal data class CapturesDto(val inicio: String, val gesto: String, val fin: String)

@Serializable
internal data class FramesMetadataDto(val nonce: String, val capturas: CapturesDto)

@Serializable
internal data class SdkMetadataDto(@SerialName("resultado_sdk") val sdkResult: String)

@Serializable
internal data class VerificationResponseDto(
    val resultado: String,
    val folio: String? = null,
    val intento: Int,
    @SerialName("intentos_restantes") val remainingAttempts: Int,
    @SerialName("dispositivo_vinculado") val deviceLinked: Boolean = false,
    @SerialName("vinculado_en") val linkedAt: String? = null,
    val motivo: String? = null,
)

@Serializable
internal data class ApiErrorDto(val codigo: String? = null)

/**
 * Activación contra el BFF Móvil con el contrato canónico (plan del backend §3).
 *
 * El correo nunca viaja en el cuerpo: el BFF lo toma del token. [demoEmail] solo se envía como
 * `X-Demo-Correo` cuando el build de debug lo configura (`solventa.demoActivationEmail`).
 */
class RemoteActivationRepository(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val demoEmail: String? = null,
) : ActivationRepository {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun startSession(accessToken: String, device: DeviceInfo, correlationId: String): SessionResult =
        withContext(ioDispatcher) {
            val body = json.encodeToString(
                StartActivationRequestDto.serializer(),
                StartActivationRequestDto(device.installationId, device.platform, device.model, device.osVersion),
            ).toRequestBody(JSON_MEDIA_TYPE)
            val request = authorized(baseUrl + START_PATH, accessToken, correlationId).post(body).build()
            try {
                client.newCall(request).execute().use { response -> parseStart(response) }
            } catch (_: IOException) {
                SessionResult.NetworkError
            } catch (_: SerializationException) {
                SessionResult.ServiceUnavailable
            } catch (_: IllegalArgumentException) {
                SessionResult.ServiceUnavailable
            }
        }

    override suspend fun verify(
        accessToken: String,
        session: ActivationSession,
        outcome: CaptureOutcome,
        correlationId: String,
    ): VerificationResult = withContext(ioDispatcher) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        when (outcome) {
            is CaptureOutcome.OwnFrames -> {
                val capture = session.capture as? CaptureType.Frames ?: return@withContext VerificationResult.InvalidSession
                val frames = outcome.frames
                if (!frames.isComplete()) return@withContext VerificationResult.InvalidCapture
                body.addFormDataPart("metadata", framesMetadata(capture.nonce, frames))
                FRAME_PARTS.forEach { (slot, name) ->
                    body.addFormDataPart(name, "$name.jpg", frames[slot]!!.bytes().toRequestBody(JPEG_MEDIA_TYPE))
                }
            }
            is CaptureOutcome.SdkResult -> body.addFormDataPart(
                "metadata",
                json.encodeToString(SdkMetadataDto.serializer(), SdkMetadataDto(outcome.status.name)),
            )
        }
        val request = authorized(baseUrl + VERIFY_PATH.format(session.sessionId), accessToken, correlationId)
            .post(body.build())
            .build()
        try {
            client.newCall(request).execute().use { response -> parseVerification(response) }
        } catch (_: IOException) {
            VerificationResult.NetworkError
        } catch (_: SerializationException) {
            VerificationResult.ServiceUnavailable
        } catch (_: IllegalArgumentException) {
            VerificationResult.ServiceUnavailable
        }
    }

    private fun authorized(url: String, accessToken: String, correlationId: String): Request.Builder =
        Request.Builder().url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("X-Correlation-Id", correlationId)
            .apply { demoEmail?.takeIf { it.isNotBlank() }?.let { header("X-Demo-Correo", it) } }

    private fun parseStart(response: Response): SessionResult {
        val text = response.body?.string().orEmpty()
        return when {
            response.isSuccessful -> toSessionResult(json.decodeFromString(StartActivationResponseDto.serializer(), text))
            response.code == 401 -> SessionResult.SessionExpired
            response.code == 403 -> SessionResult.ConsentRequired
            response.code == 404 -> SessionResult.AccountNotFound
            response.code == 429 -> SessionResult.AttemptsExhausted
            else -> SessionResult.ServiceUnavailable
        }
    }

    private fun toSessionResult(dto: StartActivationResponseDto): SessionResult {
        if (dto.deviceLinked) return SessionResult.AlreadyLinked
        val sessionId = dto.sessionId?.takeIf { it.isNotBlank() } ?: return SessionResult.ServiceUnavailable
        val expiresAt = dto.expiresAt?.takeIf { it.isNotBlank() } ?: return SessionResult.ServiceUnavailable
        val capture = when (dto.captura?.tipo) {
            "FRAMES" -> {
                val reto = dto.captura.reto ?: return SessionResult.ServiceUnavailable
                val challenge = LivenessChallenge.fromCode(reto.gesto) ?: return SessionResult.ServiceUnavailable
                CaptureType.Frames(challenge, reto.nonce)
            }
            "SDK" -> {
                val sdk = dto.captura.sdk ?: return SessionResult.ServiceUnavailable
                CaptureType.Sdk(sdk.nombre, sdk.sessionToken)
            }
            else -> return SessionResult.ServiceUnavailable
        }
        return SessionResult.Started(ActivationSession(sessionId, expiresAt, dto.remainingAttempts ?: 0, capture))
    }

    private fun parseVerification(response: Response): VerificationResult {
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful) {
            val dto = json.decodeFromString(VerificationResponseDto.serializer(), text)
            return when (dto.resultado) {
                "APROBADO" -> VerificationResult.Approved(dto.folio, dto.linkedAt)
                "RECHAZADO" -> if (dto.remainingAttempts <= 0) {
                    VerificationResult.AttemptsExhausted
                } else {
                    VerificationResult.Rejected(dto.intento, dto.remainingAttempts, dto.motivo)
                }
                else -> VerificationResult.ServiceUnavailable
            }
        }
        return when (response.code) {
            401 -> VerificationResult.SessionExpired
            404 -> VerificationResult.InvalidSession
            409 -> if (errorCode(text) == "SESION_CANCELADA") VerificationResult.Cancelled else VerificationResult.InvalidSession
            413, 415, 422 -> VerificationResult.InvalidCapture
            429 -> VerificationResult.AttemptsExhausted
            else -> VerificationResult.ServiceUnavailable
        }
    }

    private fun errorCode(text: String): String? = try {
        json.decodeFromString(ApiErrorDto.serializer(), text).codigo
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun framesMetadata(nonce: String, frames: CapturedFrames): String = json.encodeToString(
        FramesMetadataDto.serializer(),
        FramesMetadataDto(
            nonce = nonce,
            capturas = CapturesDto(
                inicio = frames[FrameSlot.START]!!.capturedAt.toString(),
                gesto = frames[FrameSlot.GESTURE]!!.capturedAt.toString(),
                fin = frames[FrameSlot.END]!!.capturedAt.toString(),
            ),
        ),
    )

    companion object {
        const val START_PATH = "/api/v1/bff-mobile/activacion/sesiones"
        const val VERIFY_PATH = "/api/v1/bff-mobile/activacion/sesiones/%s/verificacion"
        private val FRAME_PARTS = listOf(
            FrameSlot.START to "frame_inicio",
            FrameSlot.GESTURE to "frame_gesto",
            FrameSlot.END to "frame_fin",
        )
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        /** El proveedor puede tardar: subida y lectura con 30 s. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
