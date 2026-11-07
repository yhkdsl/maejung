package com.maejung.store

import android.content.Context
import com.maejung.vehicle.CabinSwitch

data class FamilyMember(val riderId: String, val riderName: String)

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

    /** 연결된 가족. `riderId\triderName` 줄 목록으로 둔다 */
    var family: List<FamilyMember>
        get() = prefs.getString(KEY_FAMILY, null).orEmpty().lines().filter { it.contains('\t') }
            .map { FamilyMember(it.substringBefore('\t'), it.substringAfter('\t')) }
        set(value) = prefs.edit().putString(KEY_FAMILY, value.joinToString("\n") { "${it.riderId}\t${it.riderName}" }).apply()

    fun addFamily(member: FamilyMember) {
        family = family.filterNot { it.riderId == member.riderId } + member
    }

    /** 차가 다시 등록되면 이전 가족 연결은 쓸 수 없다(PROTOCOL.md 3절 4401) */
    fun clearVehicle() {
        prefs.edit().remove(KEY_VEHICLE_TOKEN).remove(KEY_FAMILY).apply()
    }

    private companion object {
        const val KEY_LIGHT_BEFORE = "lightBefore"
        const val KEY_VEHICLE_TOKEN = "vehicleToken"
        const val KEY_FAMILY = "family"
    }
}
