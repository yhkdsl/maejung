package com.maejung.eta

import com.maejung.protocol.EtaSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EtaCalculatorTest {
    @Test
    fun `직선거리를 도로 거리와 시내 속도로 추정한다`() {
        // 260m × 1.3 = 338m, 338 ÷ 5.6 ≈ 60초
        assertEquals(Eta(60, 338, EtaSources.ESTIMATE), EtaCalculator.estimate(260.0))
        assertEquals(Eta(0, 0, EtaSources.ESTIMATE), EtaCalculator.estimate(0.0))
    }

    @Test
    fun `처음이면 보낸다`() {
        assertTrue(EtaCalculator.shouldSend(null, 0, Eta(60, 338, EtaSources.ESTIMATE), now = 0))
    }

    @Test
    fun `30초가 지나면 같은 값이어도 보낸다`() {
        val eta = Eta(60, 338, EtaSources.ESTIMATE)
        assertFalse(EtaCalculator.shouldSend(eta, lastSentAt = 0, current = eta, now = 29_999))
        assertTrue(EtaCalculator.shouldSend(eta, lastSentAt = 0, current = eta, now = 30_000))
    }

    @Test
    fun `시간이 20%를 넘게 바뀌면 바로 보낸다`() {
        val last = Eta(100, 560, EtaSources.ESTIMATE)
        assertFalse(EtaCalculator.shouldSend(last, 0, Eta(80, 448, EtaSources.ESTIMATE), now = 1_000))
        assertTrue(EtaCalculator.shouldSend(last, 0, Eta(79, 442, EtaSources.ESTIMATE), now = 1_000))
        assertTrue(EtaCalculator.shouldSend(last, 0, Eta(121, 678, EtaSources.ESTIMATE), now = 1_000))
    }

    @Test
    fun `출처가 바뀌면 바로 보낸다`() {
        val last = Eta(100, 560, EtaSources.ESTIMATE)
        assertTrue(EtaCalculator.shouldSend(last, 0, EtaCalculator.fromNavi(560, 100), now = 1_000))
    }
}
