package com.maejung.ui

import com.maejung.session.Phase
import com.maejung.vehicle.Gear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaitingOffersTest {
    private val sharing = Phase.Sharing("s_1", "민지")

    @Test
    fun `기어 P로 공유 중이면 혜택을 보여준다`() {
        assertEquals(DEMO_OFFERS, waitingOffers(sharing, Gear.PARK))
    }

    @Test
    fun `P가 아니거나 기어를 모르면 숨긴다`() {
        listOf(Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL, Gear.OTHER, null).forEach {
            assertTrue(waitingOffers(sharing, it).isEmpty(), "gear=$it")
        }
    }

    @Test
    fun `공유 중이 아니면 숨긴다`() {
        listOf(Phase.Idle, Phase.Requested("s_1", "민지"), Phase.Nearby("s_1", "민지"), Phase.Ended("boarded")).forEach {
            assertTrue(waitingOffers(it, Gear.PARK).isEmpty(), "phase=$it")
        }
    }
}
