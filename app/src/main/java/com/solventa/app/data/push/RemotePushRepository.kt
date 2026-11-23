package com.solventa.app.data.push

import com.solventa.app.data.claims.RemoteClaimsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

@Serializable
internal data class PushRequestDto(
    @SerialName("installation_id") val installationId: String,
    val token: String,
    val plataforma: String = "ANDROID",
)

/** `PUT`/`DELETE /api/v1/bff-mobile/dispositivos/push` (HU4.2.2 §3.1). Nunca registra el token. */
class RemotePushRepository(
    baseUrl: String,
    private val client: OkHttpClient = RemoteClaimsRepository.defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PushRepository {
    private val url = baseUrl.trimEnd('/') + PATH

    override suspend fun register(accessToken: String, installationId: String, token: String): PushResult = withContext(ioDispatcher) {
        val body = json.encodeToString(PushRequestDto.serializer(), PushRequestDto(installationId, token))
            .toRequestBody(JSON)
        execute(authorized(url, accessToken).put(body).build())
    }

    override suspend fun unregister(accessToken: String, installationId: String): PushResult = withContext(ioDispatcher) {
        val requestUrl = url.toHttpUrl().newBuilder().addQueryParameter("installation_id", installationId).build()
        execute(authorized(requestUrl.toString(), accessToken).delete().build())
    }

    private fun authorized(target: String, accessToken: String): Request.Builder = Request.Builder().url(target)
        .header("Authorization", "Bearer $accessToken")
        .header("X-Correlation-Id", UUID.randomUUID().toString())

    private fun execute(request: Request): PushResult = try {
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> PushResult.Registered
                response.code == 401 -> PushResult.SessionExpired
                response.code == 404 -> PushResult.DeviceNotLinked
                else -> PushResult.ServiceUnavailable
            }
        }
    } catch (_: IOException) {
        PushResult.NetworkError
    }

    companion object {
        const val PATH = "/api/v1/bff-mobile/dispositivos/push"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val json = Json { encodeDefaults = true }
    }
}
