package com.maejung.session

import com.maejung.protocol.EndReasons
import com.maejung.protocol.PeerLocation
import com.maejung.protocol.PickupAccept
import com.maejung.protocol.PickupReject
import com.maejung.protocol.PickupRequested
import com.maejung.protocol.Received
import com.maejung.protocol.ServerMessage
import com.maejung.protocol.SessionBoarded
import com.maejung.protocol.SessionEnd
import com.maejung.protocol.SessionEnded
import com.maejung.protocol.SessionState
import com.maejung.protocol.SessionStates
import com.maejung.protocol.VehicleLight
import com.maejung.session.CarEffect.DismissRequestAlert
import com.maejung.session.CarEffect.Listen
import com.maejung.session.CarEffect.Notify
import com.maejung.session.CarEffect.SaveLightBefore
import com.maejung.session.CarEffect.Send
import com.maejung.session.CarEffect.SetCabinLight
import com.maejung.session.CarEffect.ShowRequestAlert
import com.maejung.session.CarEffect.Speak
import com.maejung.session.CarEffect.StartLocationSharing
import com.maejung.session.CarEffect.StartNavigation
import com.maejung.session.CarEffect.StopLocationSharing
import com.maejung.session.CarEffect.StopNavigation
import com.maejung.vehicle.CabinLightInfo
import com.maejung.vehicle.CabinSwitch
import com.maejung.vehicle.Gear
import com.maejung.vehicle.PassengerDoor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CarSessionMachineTest {
    private var session = CarSession()
    private var effects: List<CarEffect> = emptyList()

    private fun on(event: CarEvent): List<CarEffect> {
        val (next, out) = CarSessionMachine.reduce(session, event)
        session = next
        effects = out
        return out
    }

    private fun server(message: ServerMessage, sessionId: String? = "s_1") = on(CarEvent.Server(Received(message, sessionId, 0)))

    private val phase get() = session.phase

    // ---- 상황 만들기 ----

    private fun lightSupported(current: CabinSwitch = CabinSwitch.OFF) = on(CarEvent.CabinLight(CabinLightInfo(current, supportsOn = true)))

    private fun requested() = server(PickupRequested("r_1", "민지"))

    private fun sharing() {
        requested()
        on(CarEvent.UserAccept)
    }

    /** 학원 앞 대기: 기어 P, 탑승자 위치가 먼저 오고 거리가 계산된다 */
    private fun approach(meters: Double) {
        server(PeerLocation(37.4983, 127.0628, 5f))
        on(CarEvent.Distance(meters))
    }

    private fun nearby(current: CabinSwitch = CabinSwitch.OFF) {
        lightSupported(current)
        on(CarEvent.GearChanged(Gear.PARK))
        sharing()
        approach(120.0)
        on(CarEvent.Distance(40.0))
    }

    // ---- Idle ----

    @Test
    fun `요청이 오면 알림을 띄우고 음성으로 묻는다`() {
        requested()
        assertEquals(Phase.Requested("s_1", "민지"), phase)
        assertEquals(listOf(ShowRequestAlert("민지"), Speak("민지가 데리러 와 달래요. 갈까요?"), Listen), effects)
    }

    @Test
    fun `받침 있는 이름은 이가로 부른다`() {
        server(PickupRequested("r_2", "준영"))
        assertEquals(Speak("준영이가 데리러 와 달래요. 갈까요?"), effects[1])
    }

    @Test
    fun `Idle에서 session_state requested는 음성 없이 요청 상태로`() {
        server(SessionState(SessionStates.REQUESTED, "r_1", "민지"))
        assertEquals(Phase.Requested("s_1", "민지"), phase)
        assertEquals(emptyList(), effects)
    }

    @Test
    fun `Idle에서 session_state sharing은 위치 전송을 시작한다`() {
        server(SessionState(SessionStates.SHARING, "r_1", "민지"))
        assertEquals(Phase.Sharing("s_1", "민지"), phase)
        assertEquals(listOf(StartLocationSharing), effects)
    }

    @Test
    fun `Idle에서 표에 없는 이벤트는 아무것도 하지 않는다`() {
        on(CarEvent.Door(PassengerDoor.ROW_2_RIGHT, true))
        on(CarEvent.UserAccept)
        on(CarEvent.UserEnd)
        server(SessionEnded(EndReasons.BOARDED))
        assertEquals(Phase.Idle, phase)
        assertEquals(emptyList(), effects)
    }

    // ---- Requested ----

    @Test
    fun `수락하면 알림을 닫고 서버에 알리고 위치 전송을 시작한다`() {
        sharing()
        assertEquals(Phase.Sharing("s_1", "민지"), phase)
        assertEquals(listOf(DismissRequestAlert, Send(PickupAccept), StartLocationSharing), effects)
    }

    @Test
    fun `거절하면 rejected로 끝나고 알림을 닫는다`() {
        requested()
        on(CarEvent.UserReject)
        assertEquals(Phase.Ended(EndReasons.REJECTED), phase)
        assertEquals(listOf(Send(PickupReject), DismissRequestAlert), effects)
    }

    @Test
    fun `요청 중 서버가 끝내면 알림만 닫는다`() {
        requested()
        server(SessionEnded(EndReasons.REQUEST_TIMEOUT))
        assertEquals(Phase.Ended(EndReasons.REQUEST_TIMEOUT), phase)
        assertEquals(listOf(DismissRequestAlert), effects)
    }

    @Test
    fun `요청 중 재연결해서 sharing이면 알림을 닫고 공유를 시작한다`() {
        requested()
        server(SessionState(SessionStates.SHARING, "r_1", "민지"))
        assertEquals(Phase.Sharing("s_1", "민지"), phase)
        assertEquals(listOf(DismissRequestAlert, StartLocationSharing), effects)
    }

    @Test
    fun `요청 중 session_state none이면 peer_lost`() {
        requested()
        server(SessionState(SessionStates.NONE))
        assertEquals(Phase.Ended(EndReasons.PEER_LOST), phase)
    }

    @Test
    fun `요청 중 차에서 종료하면 manual`() {
        requested()
        on(CarEvent.UserEnd)
        assertEquals(Phase.Ended(EndReasons.MANUAL), phase)
        assertEquals(listOf(Send(SessionEnd(EndReasons.MANUAL)), DismissRequestAlert), effects)
    }

    // ---- 길안내 ----

    @Test
    fun `처음 계산한 거리가 500m 이상이면 탑승자 위치로 길안내를 한 번 시작한다`() {
        sharing()
        approach(800.0)
        assertTrue(session.navStarted)
        assertEquals(listOf(StartNavigation(37.4983, 127.0628, "민지"), Speak("민지에게 가는 길을 안내할게요")), effects)

        approach(900.0)
        assertEquals(emptyList(), effects)
    }

    @Test
    fun `정확히 500m면 길안내를 시작한다`() {
        sharing()
        approach(500.0)
        assertTrue(effects.any { it is StartNavigation })
    }

    @Test
    fun `처음 거리가 500m 미만이면 나중에 멀어져도 길안내하지 않는다`() {
        sharing()
        approach(499.0)
        assertFalse(session.navStarted)
        assertEquals(emptyList(), effects)
        approach(1200.0)
        assertEquals(emptyList(), effects)
    }

    @Test
    fun `길안내 중 종료하면 길안내도 취소한다`() {
        sharing()
        approach(800.0)
        on(CarEvent.UserEnd)
        assertEquals(listOf(Send(SessionEnd(EndReasons.MANUAL)), StopLocationSharing, StopNavigation), effects)
        assertFalse(session.navStarted)
    }

    @Test
    fun `새 요청은 길안내 판단을 다시 한다`() {
        sharing()
        approach(100.0)
        on(CarEvent.UserEnd)
        on(CarEvent.EndedTimeout)
        sharing()
        approach(800.0)
        assertTrue(effects.any { it is StartNavigation })
    }

    // ---- Nearby 진입·이탈 ----

    @Test
    fun `50m 안에 기어 P면 실내등을 켜고 알린다`() {
        nearby()
        assertEquals(Phase.Nearby("s_1", "민지"), phase)
        assertEquals(
            listOf(
                SaveLightBefore(CabinSwitch.OFF),
                SetCabinLight(CabinSwitch.ON),
                Send(VehicleLight(on = true)),
                Notify("민지가 가까이 왔어요"),
                Speak("민지가 가까이 왔어요"),
            ),
            effects,
        )
        assertEquals(CabinSwitch.OFF, session.lightBefore)
    }

    @Test
    fun `정확히 50m는 들어가고 51m는 안 들어간다`() {
        lightSupported()
        on(CarEvent.GearChanged(Gear.PARK))
        sharing()
        approach(51.0)
        assertIs<Phase.Sharing>(phase)
        on(CarEvent.Distance(50.0))
        assertIs<Phase.Nearby>(phase)
    }

    @Test
    fun `가까워도 기어가 P가 아니면 들어가지 않고, P로 바꾸는 순간 들어간다`() {
        lightSupported()
        on(CarEvent.GearChanged(Gear.DRIVE))
        sharing()
        approach(30.0)
        assertIs<Phase.Sharing>(phase)
        on(CarEvent.GearChanged(Gear.PARK))
        assertIs<Phase.Nearby>(phase)
    }

    @Test
    fun `70m까지는 머물고 70m를 넘으면 공유 상태로 돌아가며 실내등을 복구한다`() {
        nearby()
        on(CarEvent.Distance(70.0))
        assertIs<Phase.Nearby>(phase)
        on(CarEvent.Distance(70.1))
        assertEquals(Phase.Sharing("s_1", "민지"), phase)
        assertEquals(
            listOf(SetCabinLight(CabinSwitch.OFF), SaveLightBefore(null), Send(VehicleLight(on = false))),
            effects,
        )
        assertEquals(null, session.lightBefore)
    }

    @Test
    fun `출발하면(기어 D) 공유 상태로 돌아간다`() {
        nearby()
        on(CarEvent.GearChanged(Gear.DRIVE))
        assertIs<Phase.Sharing>(phase)
        assertTrue(effects.contains(SetCabinLight(CabinSwitch.OFF)))
    }

    @Test
    fun `거리를 모르게 되면 공유 상태로 돌아간다`() {
        nearby()
        on(CarEvent.Distance(null))
        assertIs<Phase.Sharing>(phase)
    }

    @Test
    fun `실내등을 지원하지 않으면 실내등 효과와 vehicle_light 없이 들어간다`() {
        on(CarEvent.CabinLight(CabinLightInfo(CabinSwitch.OFF, supportsOn = false)))
        on(CarEvent.GearChanged(Gear.PARK))
        sharing()
        approach(40.0)
        assertIs<Phase.Nearby>(phase)
        assertEquals(listOf(Notify("민지가 가까이 왔어요"), Speak("민지가 가까이 왔어요")), effects)
        on(CarEvent.Distance(100.0))
        assertEquals(emptyList(), effects)
    }

    @Test
    fun `실내등 값을 아직 모르면 꺼져 있던 것으로 본다`() {
        on(CarEvent.CabinLight(CabinLightInfo(null, supportsOn = true)))
        on(CarEvent.GearChanged(Gear.PARK))
        sharing()
        approach(40.0)
        assertEquals(CabinSwitch.OFF, session.lightBefore)
    }

    @Test
    fun `원래 켜져 있었으면 복구해도 켜진 채로 둔다`() {
        nearby(current = CabinSwitch.ON)
        on(CarEvent.Distance(100.0))
        assertEquals(listOf(SetCabinLight(CabinSwitch.ON), SaveLightBefore(null), Send(VehicleLight(on = true))), effects)
    }

    @Test
    fun `원래 AUTOMATIC이었으면 AUTOMATIC으로 되돌린다`() {
        nearby(current = CabinSwitch.AUTOMATIC)
        on(CarEvent.UserEnd)
        assertTrue(effects.contains(SetCabinLight(CabinSwitch.AUTOMATIC)))
        assertTrue(effects.none { it is Send && it.message is VehicleLight })
    }

    @Test
    fun `앱 재시작으로 복원한 원래 값은 다시 들어가도 덮어쓰지 않는다`() {
        // Prefs에 남아 있던 원래 값(AUTOMATIC)으로 시작했고, 지금 실내등은 앱이 켜 둔 ON 상태
        session = CarSession(lightBefore = CabinSwitch.AUTOMATIC)
        lightSupported(current = CabinSwitch.ON)
        on(CarEvent.GearChanged(Gear.PARK))
        server(SessionState(SessionStates.SHARING, "r_1", "민지"))
        approach(40.0)
        assertIs<Phase.Nearby>(phase)
        assertEquals(CabinSwitch.AUTOMATIC, session.lightBefore)
        assertTrue(effects.none { it is SaveLightBefore })
        assertTrue(effects.contains(SetCabinLight(CabinSwitch.ON)))
    }

    // ---- 탑승 ----

    @Test
    fun `문을 열었다 닫으면 30m 이내·기어 P에서 탑승으로 끝난다`() {
        nearby()
        on(CarEvent.Distance(25.0))
        on(CarEvent.Door(PassengerDoor.ROW_2_RIGHT, open = true))
        assertEquals(Phase.Nearby("s_1", "민지", setOf(PassengerDoor.ROW_2_RIGHT)), phase)
        on(CarEvent.Door(PassengerDoor.ROW_2_RIGHT, open = false))
        assertEquals(Phase.Ended(EndReasons.BOARDED), phase)
        assertEquals(
            listOf(
                Send(SessionBoarded),
                SetCabinLight(CabinSwitch.OFF),
                SaveLightBefore(null),
                StopLocationSharing,
                Notify("민지 탑승 확인, 위치공유를 끝낼게요"),
                Speak("민지 탑승 확인, 위치공유를 끝낼게요"),
            ),
            effects,
        )
    }

    @Test
    fun `30m보다 멀면 문을 닫아도 탑승이 아니다`() {
        nearby()
        on(CarEvent.Door(PassengerDoor.ROW_2_LEFT, open = true))
        on(CarEvent.Door(PassengerDoor.ROW_2_LEFT, open = false))
        assertIs<Phase.Nearby>(phase)
        assertEquals(emptyList(), effects)
    }

    @Test
    fun `Nearby 전에 열린 문은 세지 않는다`() {
        lightSupported()
        on(CarEvent.GearChanged(Gear.PARK))
        sharing()
        approach(200.0)
        on(CarEvent.Door(PassengerDoor.ROW_1_RIGHT, open = true))
        on(CarEvent.Distance(20.0))
        on(CarEvent.Door(PassengerDoor.ROW_1_RIGHT, open = false))
        assertIs<Phase.Nearby>(phase)
    }

    @Test
    fun `다른 문이 닫히는 것은 세지 않는다`() {
        nearby()
        on(CarEvent.Distance(20.0))
        on(CarEvent.Door(PassengerDoor.ROW_2_RIGHT, open = true))
        on(CarEvent.Door(PassengerDoor.ROW_2_LEFT, open = false))
        assertIs<Phase.Nearby>(phase)
    }

    @Test
    fun `Nearby를 나가면 열린 문 기록을 비운다`() {
        nearby()
        on(CarEvent.Door(PassengerDoor.ROW_2_RIGHT, open = true))
        on(CarEvent.Distance(100.0))
        on(CarEvent.Distance(20.0))
        on(CarEvent.Door(PassengerDoor.ROW_2_RIGHT, open = false))
        assertIs<Phase.Nearby>(phase)
    }

    // ---- 종료 ----

    @Test
    fun `공유 중 서버가 끝내면 실내등을 서버에 알리지 않고 복구한다`() {
        nearby()
        server(SessionEnded(EndReasons.PEER_LOST))
        assertEquals(Phase.Ended(EndReasons.PEER_LOST), phase)
        assertEquals(listOf(SetCabinLight(CabinSwitch.OFF), SaveLightBefore(null), StopLocationSharing), effects)
    }

    @Test
    fun `공유 중 session_state none이면 peer_lost로 정리한다`() {
        nearby()
        server(SessionState(SessionStates.NONE))
        assertEquals(Phase.Ended(EndReasons.PEER_LOST), phase)
        assertTrue(effects.contains(SetCabinLight(CabinSwitch.OFF)))
    }

    @Test
    fun `공유 중 차에서 종료하면 manual`() {
        sharing()
        on(CarEvent.UserEnd)
        assertEquals(Phase.Ended(EndReasons.MANUAL), phase)
        assertEquals(listOf(Send(SessionEnd(EndReasons.MANUAL)), StopLocationSharing), effects)
    }

    @Test
    fun `종료 3초 뒤 Idle로 돌아가고, 그 전에 새 요청이 오면 바로 받는다`() {
        sharing()
        on(CarEvent.UserEnd)
        on(CarEvent.EndedTimeout)
        assertEquals(Phase.Idle, phase)

        sharing()
        on(CarEvent.UserEnd)
        server(PickupRequested("r_1", "민지"), sessionId = "s_2")
        assertEquals(Phase.Requested("s_2", "민지"), phase)
        assertEquals(ShowRequestAlert("민지"), effects.first())
    }

    @Test
    fun `내가 탑승으로 끝낸 뒤 서버의 session_ended는 무시한다`() {
        nearby()
        on(CarEvent.Distance(20.0))
        on(CarEvent.Door(PassengerDoor.ROW_2_RIGHT, open = true))
        on(CarEvent.Door(PassengerDoor.ROW_2_RIGHT, open = false))
        server(SessionEnded(EndReasons.BOARDED))
        assertEquals(Phase.Ended(EndReasons.BOARDED), phase)
        assertEquals(emptyList(), effects)
    }

    @Test
    fun `공유 중 표에 없는 이벤트는 무시한다`() {
        sharing()
        on(CarEvent.UserAccept)
        on(CarEvent.UserReject)
        on(CarEvent.Door(PassengerDoor.ROW_2_RIGHT, open = true))
        assertEquals(Phase.Sharing("s_1", "민지"), phase)
        assertEquals(emptyList(), effects)
    }
}
