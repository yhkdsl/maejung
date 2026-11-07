package com.maejung.store

import android.content.Context
import com.maejung.vehicle.CabinSwitch

/** 앱이 죽었다 살아나도 남아야 하는 값 */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("maejung", Context.MODE_PRIVATE)

    /** Nearby에서 실내등을 켜기 전 원래 값. 앱이 Nearby 도중 죽어도 복구할 수 있게 남긴다 */
    var lightBefore: CabinSwitch?
        get() = prefs.getString(KEY_LIGHT_BEFORE, null)?.let { runCatching { CabinSwitch.valueOf(it) }.getOrNull() }
        set(value) = prefs.edit().putString(KEY_LIGHT_BEFORE, value?.name).apply()

    var vehicleToken: String?
        get() = prefs.getString(KEY_VEHICLE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_VEHICLE_TOKEN, value).apply()

    private companion object {
        const val KEY_LIGHT_BEFORE = "lightBefore"
        const val KEY_VEHICLE_TOKEN = "vehicleToken"
    }
}
