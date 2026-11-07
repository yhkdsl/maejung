package com.maejung.location

import kotlinx.coroutines.flow.Flow

data class Fix(val lat: Double, val lng: Double, val accuracy: Float, val time: Long)

/** 차 위치. 지금은 LocationManager, FusedLocation SDK로 바꿀 수 있게 감싼다 */
interface LocationSource {
    val fixes: Flow<Fix>

    fun start()

    fun stop()
}
