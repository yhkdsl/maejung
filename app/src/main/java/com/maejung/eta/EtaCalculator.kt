package com.maejung.eta

import com.maejung.protocol.EtaSources
import kotlin.math.abs
import kotlin.math.roundToInt

data class Eta(val seconds: Int, val meters: Int, val source: String)

/**
 * 도착 예정 시간(docs/CAR_APP.md 2절 eta/). 길안내 중이면 내비 값을 그대로 쓰고,
 * 아니면 직선거리 × 1.3(도로 우회) ÷ 5.6m/s(시내 20km/h)로 추정한다.
 * 보내는 시점은 PROTOCOL.md 3.2: 30초마다, 또는 값이 20% 넘게 바뀔 때.
 */
object EtaCalculator {
    private const val ROAD_FACTOR = 1.3
    private const val CITY_SPEED_MPS = 5.6
    const val RESEND_INTERVAL_MS = 30_000L
    private const val CHANGE_RATIO = 0.2

    fun estimate(straightMeters: Double): Eta {
        val roadMeters = straightMeters * ROAD_FACTOR
        return Eta(seconds = (roadMeters / CITY_SPEED_MPS).roundToInt(), meters = roadMeters.roundToInt(), source = EtaSources.ESTIMATE)
    }

    fun fromNavi(meters: Int, seconds: Int) = Eta(seconds = seconds, meters = meters, source = EtaSources.NAVI)

    /** 처음이거나, 30초가 지났거나, 시간이 20% 넘게 바뀌었거나, 출처가 바뀌면 보낸다 */
    fun shouldSend(last: Eta?, lastSentAt: Long, current: Eta, now: Long): Boolean {
        if (last == null) return true
        if (now - lastSentAt >= RESEND_INTERVAL_MS) return true
        if (last.source != current.source) return true
        val base = maxOf(last.seconds, 1)
        return abs(current.seconds - last.seconds).toDouble() / base > CHANGE_RATIO
    }
}
