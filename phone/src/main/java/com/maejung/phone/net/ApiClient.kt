package com.maejung.phone.net

import com.maejung.protocol.ApiError
import com.maejung.protocol.ApiPaths
import com.maejung.protocol.ErrorCodes
import com.maejung.protocol.PairingRequest
import com.maejung.protocol.PairingResult
import com.maejung.protocol.Protocol
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class ApiException(val status: Int, val code: String?, message: String) : IOException(message)

/** Phone pairing REST API, using the shared wire DTOs and JSON settings. */
class ApiClient(
    private val serverUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) : PairingApi {
    override suspend fun pair(code: String, riderName: String): PairingResult {
        val name = riderName.trim()
        if (name.length !in 1..20) {
            throw ApiException(400, ErrorCodes.INVALID_RIDER_NAME, "Invalid rider name")
        }
        if (code.length != 6 || code.any { it !in '0'..'9' }) {
            throw ApiException(404, ErrorCodes.PAIRING_CODE_NOT_FOUND, "Invalid pairing code")
        }
        val request = Request.Builder()
            .url(serverUrl.toHttpUrl().newBuilder().encodedPath(ApiPaths.PAIRINGS).query(null).fragment(null).build())
            .post(Protocol.json.encodeToString(PairingRequest.serializer(), PairingRequest(code, name))
                .toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if (!continuation.isActive) return
                            val body = it.body?.string().orEmpty()
                            if (!it.isSuccessful) {
                                val error = runCatching { Protocol.json.decodeFromString(ApiError.serializer(), body) }.getOrNull()
                                throw ApiException(it.code, error?.code, error?.message ?: "HTTP ${it.code}")
                            }
                            val decoded = runCatching { Protocol.json.decodeFromString(PairingResult.serializer(), body) }
                                .getOrNull() ?: throw IOException("Invalid pairing response")
                            if (decoded.riderId.isBlank() || decoded.vehicleId.isBlank() || decoded.riderToken.isBlank()) {
                                throw IOException("Invalid pairing response")
                            }
                            decoded
                        }
                        continuation.resume(result)
                    } catch (error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }
}
