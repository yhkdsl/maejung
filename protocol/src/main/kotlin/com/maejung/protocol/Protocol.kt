package com.maejung.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

/** 받은 메시지와 봉투 정보 */
data class Received(val message: Message, val sessionId: String?, val ts: Long)

/**
 * 봉투 `{type, sessionId, ts, payload}`를 만들고 푼다(docs/PROTOCOL.md 3절).
 * 다형 직렬화의 classDiscriminator는 모르는 type에서 예외를 던져서, type을 먼저 읽고 직접 나눈다.
 */
object Protocol {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private class Entry<T : Message>(val type: String, val kclass: Class<T>, val serializer: KSerializer<T>)

    private inline fun <reified T : Message> entry(type: String) = Entry(type, T::class.java, serializer<T>())

    private val entries = listOf(
        entry<PickupRequest>("pickup.request"),
        entry<PickupAccept>("pickup.accept"),
        entry<PickupReject>("pickup.reject"),
        entry<EtaUpdate>("eta.update"),
        entry<VehicleLight>("vehicle.light"),
        entry<SessionBoarded>("session.boarded"),
        entry<LocationUpdate>("location.update"),
        entry<SessionEnd>("session.end"),
        entry<SessionState>("session.state"),
        entry<PickupRequested>("pickup.requested"),
        entry<PickupPending>("pickup.pending"),
        entry<PickupAccepted>("pickup.accepted"),
        entry<PeerLocation>("peer.location"),
        entry<PeerEta>("peer.eta"),
        entry<PeerLight>("peer.light"),
        entry<SessionEnded>("session.ended"),
        entry<PairingCompleted>("pairing.completed"),
        entry<ErrorMessage>("error"),
    )
    private val byType = entries.associateBy { it.type }
    private val byClass = entries.associateBy { it.kclass }

    fun typeOf(message: Message): String = when (message) {
        is Unknown -> message.type
        else -> byClass.getValue(message.javaClass).type
    }

    fun encode(message: Message, ts: Long, sessionId: String? = null): String {
        require(message !is Unknown) { "Unknown은 보낼 수 없습니다" }
        val payload = encodePayload(message)
        val envelope = buildJsonObject {
            put("type", typeOf(message))
            sessionId?.let { put("sessionId", it) }
            put("ts", ts)
            put("payload", payload)
        }
        return envelope.toString()
    }

    /** JSON이 아니거나 봉투 형식이 틀리면 null. 모르는 type은 [Unknown] */
    fun decode(text: String): Received? {
        val envelope = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val type = runCatching { envelope["type"]?.jsonPrimitive?.content }.getOrNull() ?: return null
        val sessionId = runCatching { envelope["sessionId"]?.jsonPrimitive?.content }.getOrNull()
        val ts = runCatching { envelope["ts"]?.jsonPrimitive?.longOrNull }.getOrNull() ?: 0L
        val payload = envelope["payload"] as? JsonObject ?: JsonObject(emptyMap())
        val entry = byType[type] ?: return Received(Unknown(type, text), sessionId, ts)
        val message = runCatching { json.decodeFromJsonElement(entry.serializer, payload) }.getOrNull() ?: return null
        return Received(message, sessionId, ts)
    }

    @Suppress("UNCHECKED_CAST")
    private fun encodePayload(message: Message): JsonObject {
        val entry = byClass.getValue(message.javaClass) as Entry<Message>
        // data object는 빈 객체 {}로 나간다
        return json.encodeToJsonElement(entry.serializer, message) as? JsonObject ?: JsonObject(emptyMap())
    }
}
