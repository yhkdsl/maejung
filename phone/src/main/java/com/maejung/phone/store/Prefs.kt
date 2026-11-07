package com.maejung.phone.store

import android.content.Context
import android.content.SharedPreferences
import com.maejung.protocol.Protocol
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A whole pairing is one preference value, so no reader can observe mixed credentials. */
class Prefs internal constructor(private val preferences: SharedPreferences) : PairingStore {
    constructor(context: Context) : this(context.getSharedPreferences("maejung_phone", Context.MODE_PRIVATE))

    override fun load(): StoredPairing? = runCatching {
        val stored = preferences.getString(KEY_PAIRING, null) ?: return@runCatching null
        val fields = Protocol.json.parseToJsonElement(stored).jsonObject
        fun field(key: String): String = fields.getValue(key).jsonPrimitive
            .takeIf { it.isString }?.content ?: error("Invalid pairing field")
        StoredPairing(field("riderId"), field("vehicleId"), field("riderToken"), field("riderName"))
            .takeIf {
                it.riderId.isNotBlank() && it.vehicleId.isNotBlank() && it.riderToken.isNotBlank() &&
                    it.riderName.trim().length in 1..20
            }
    }.getOrNull()

    override fun save(pairing: StoredPairing) {
        val snapshot = buildJsonObject {
            put("riderId", pairing.riderId)
            put("vehicleId", pairing.vehicleId)
            put("riderToken", pairing.riderToken)
            put("riderName", pairing.riderName)
        }.toString()
        preferences.edit().putString(KEY_PAIRING, snapshot).apply()
    }

    override fun clear() {
        preferences.edit().remove(KEY_PAIRING).apply()
    }

    private companion object {
        const val KEY_PAIRING = "pairing"
    }
}
