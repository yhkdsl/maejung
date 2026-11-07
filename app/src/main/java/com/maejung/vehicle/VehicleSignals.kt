package com.maejung.vehicle

import kotlinx.coroutines.flow.Flow

/** SDK `LightSwitch`의 앱 쪽 사본. 상태 머신이 SDK 타입에 의존하지 않게 하고, 원래 값을 그대로 복구하려고 enum으로 둔다 */
enum class CabinSwitch { OFF, ON, DAYTIME_RUNNING, AUTOMATIC }

data class CabinLightInfo(val current: CabinSwitch?, val supportsOn: Boolean)

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, OTHER }

/** 탑승 판정에 쓰는 승객 문(운전석 제외) */
enum class PassengerDoor { ROW_1_RIGHT, ROW_2_LEFT, ROW_2_RIGHT }

data class DoorPosition(val door: PassengerDoor, val open: Boolean)

/** 차량 신호 읽기와 실내등 제어(docs/CAR_APP.md 2절). 구현: Pleos Vehicle SDK, 테스트용 가짜 */
interface VehicleSignals {
    val gear: Flow<Gear>
    val doors: Flow<DoorPosition>

    /** 실내등 지원 여부와 현재 스위치 값. 앱 시작·Sharing 진입 때 부른다 */
    suspend fun readCabinLight(): CabinLightInfo

    suspend fun setCabinLight(value: CabinSwitch)

    fun release()
}
