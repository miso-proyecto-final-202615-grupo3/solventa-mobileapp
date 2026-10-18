package com.solventa.app.data.auth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
internal data class LoginRequestDto(
    val correo: String,
    val password: String,
)

@Serializable
internal data class LoginResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val correo: String? = null,
)

/**
 * Autenticación contra el BFF Móvil (`POST /api/v1/bff-mobile/auth/login`).
 *
 * El BFF aún no entrega id, nombre, estado KYC ni vinculación del dispositivo:
 * el MS Identidad no expone una consulta del cliente autenticado y la vinculación
 * llega con HU3.1.1. Mientras tanto se usa [assumeDeviceLinked] para poder ofrecer la biometría.
 */
class RemoteAuthRepository(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val assumeDeviceLinked: Boolean = true,
) : AuthRepository {

    private val loginUrl = baseUrl.trimEnd('/') + LOGIN_PATH

    override suspend fun login(email: String, password: String): LoginResult = withContext(ioDispatcher) {
        val body = json.encodeToString(LoginRequestDto.serializer(), LoginRequestDto(email, password))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(loginUrl).post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val dto = json.decodeFromString(LoginResponseDto.serializer(), response.body?.string().orEmpty())
                        LoginResult.Success(dto.toSession())
                    }
                    // 401: credenciales inválidas. Cognito no distingue "cuenta no existe", a propósito.
                    response.code == 401 || response.code == 422 -> LoginResult.InvalidCredentials
                    // 503 cuando el MS Identidad o Cognito no responden; el teléfono sí tiene conexión.
                    else -> LoginResult.ServiceUnavailable
                }
            }
        } catch (_: SerializationException) {
            LoginResult.ServiceUnavailable
        } catch (_: IOException) {
            LoginResult.NetworkError
        } catch (_: IllegalArgumentException) {
            LoginResult.ServiceUnavailable
        }
    }

    private fun LoginResponseDto.toSession() = Session(
        accessToken = accessToken,
        refreshToken = refreshToken.orEmpty(),
        expiresInSeconds = expiresIn ?: 0,
        cliente = ClienteResumen(
            id = "",
            nombre = "",
            estadoKyc = EstadoKyc.PENDIENTE,
            dispositivoVinculado = assumeDeviceLinked,
            correo = correo,
        ),
    )

    companion object {
        const val LOGIN_PATH = "/api/v1/bff-mobile/auth/login"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
