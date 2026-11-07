package com.maejung.net

import com.maejung.protocol.ApiError
import com.maejung.protocol.ApiPaths
import com.maejung.protocol.PairingCodeIssued
import com.maejung.protocol.Protocol
import com.maejung.protocol.VehicleRegistered
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ApiException(val status: Int, val code: String?, message: String) : IOException(message)

/** 차 쪽 REST(docs/PROTOCOL.md 2절): 차 등록, 가족 연결 코드 발급 */
class ApiClient(private val serverUrl: String, private val client: OkHttpClient = OkHttpClient()) {

    suspend fun registerVehicle(): VehicleRegistered = post(ApiPaths.VEHICLES, token = null)

    suspend fun issuePairingCode(vehicleToken: String): PairingCodeIssued = post(ApiPaths.PAIRING_CODES, vehicleToken)

    private suspend inline fun <reified T> post(path: String, token: String?): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(serverUrl + path)
            .post(ByteArray(0).toRequestBody())
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching { Protocol.json.decodeFromString<ApiError>(body) }.getOrNull()
                throw ApiException(response.code, error?.code, error?.message ?: "HTTP ${response.code}")
            }
            Protocol.json.decodeFromString<T>(body)
        }
    }
}
