package com.maejung.server.token

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 토큰 주인. 서버는 토큰을 저장하지 않고 서명만 검증한다(PROTOCOL.md 1절) */
sealed interface Principal {
    val key: String
    val vehicleId: String

    data class Vehicle(override val vehicleId: String) : Principal {
        override val key get() = vehicleId
    }

    data class Rider(override val vehicleId: String, val riderId: String) : Principal {
        override val key get() = riderId
    }
}

@Component
class TokenService(
    @Value("\${maejung.token-secret}") secret: String,
    private val objectMapper: ObjectMapper,
) {
    private val key = SecretKeySpec(secret.toByteArray(), ALGORITHM)
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun issueVehicle(vehicleId: String): String = sign(mapOf("k" to "v", "vid" to vehicleId))

    fun issueRider(vehicleId: String, riderId: String): String =
        sign(mapOf("k" to "r", "vid" to vehicleId, "rid" to riderId))

    /** 서명이 맞으면 주인을, 형식·서명이 틀리면 null을 돌려준다 */
    fun verify(token: String?): Principal? {
        if (token.isNullOrBlank()) return null
        val parts = token.split(".")
        if (parts.size != 2) return null
        val payload = runCatching { decoder.decode(parts[0]) }.getOrNull() ?: return null
        val signature = runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(hmac(payload), signature)) return null
        val claims = runCatching { objectMapper.readValue(payload, Map::class.java) }.getOrNull() ?: return null
        val vehicleId = claims["vid"] as? String ?: return null
        return when (claims["k"]) {
            "v" -> Principal.Vehicle(vehicleId)
            "r" -> (claims["rid"] as? String)?.let { Principal.Rider(vehicleId, it) }
            else -> null
        }
    }

    private fun sign(claims: Map<String, String>): String {
        val payload = objectMapper.writeValueAsBytes(claims)
        return encoder.encodeToString(payload) + "." + encoder.encodeToString(hmac(payload))
    }

    private fun hmac(payload: ByteArray): ByteArray = Mac.getInstance(ALGORITHM).apply { init(key) }.doFinal(payload)

    companion object {
        private const val ALGORITHM = "HmacSHA256"

        /** `v_`·`r_`·`s_` 접두사 + 하이픈을 뺀 UUID 앞 12자 */
        fun newId(prefix: String): String = prefix + "_" + UUID.randomUUID().toString().replace("-", "").take(12)
    }
}
