package com.maejung.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Pleos Vehicle SDK로 기어·승객 문·실내등을 다룬다(docs/DESIGN.md 5절, 스파이크에서 확인한 시그니처) */
class PleosVehicleSignals(context: Context) : VehicleSignals {
    private val vehicle = Vehicle(context).apply { initialize() }

    override val gear: Flow<Gear> = callbackFlow {
        val listener = object : GearSelectionListener {
            override fun onSelected(selection: VehicleGear?) {
                trySend(selection.toGear())
            }

            override fun onFailed(e: Exception) = Log.w(TAG, "기어 구독 실패", e).let { }
        }
        val api = vehicle.getGear()
        api.registerGearSelection(listener)
        awaitClose { api.unregisterGearSelection(listener) }
    }

    override val doors: Flow<DoorPosition> = merge(*PassengerDoor.entries.map(::doorFlow).toTypedArray())

    private fun doorFlow(door: PassengerDoor): Flow<DoorPosition> = callbackFlow {
        val area = DoorArea(door.toDoorValue())
        val listener = object : DoorPositionListener {
            // 승객 문 범위는 0(닫힘)~1(열림)
            override fun onPositionUpdated(pos: Int?) {
                if (pos != null) trySend(DoorPosition(door, open = pos > 0))
            }

            override fun onFailed(e: Exception) = Log.w(TAG, "문 구독 실패 $door", e).let { }
        }
        val api = vehicle.getDoor()
        api.registerDoorPosition(area, listener)
        awaitClose { api.unregisterDoorPosition(area, listener) }
    }

    override suspend fun readCabinLight(): CabinLightInfo {
        val light = vehicle.getLight()
        val supported = suspendCancellableCoroutine { cont ->
            light.checkCabinLightSwitchCapabilities(
                onComplete = { switches: List<LightSwitch> -> cont.resume(switches) },
                onFailed = { cont.resume(emptyList()) },
            )
        }
        val current = suspendCancellableCoroutine { cont ->
            light.getCabinLightSwitch(
                onComplete = { value -> cont.resume(value?.toCabinSwitch()) },
                onFailed = { cont.resume(null) },
            )
        }
        return CabinLightInfo(current, supportsOn = LightSwitch.ON in supported)
    }

    override suspend fun setCabinLight(value: CabinSwitch) {
        suspendCancellableCoroutine { cont ->
            vehicle.getLight().setCabinLightSwitch(
                value.toLightSwitch(),
                onComplete = { cont.resume(Unit) },
                onFailed = {
                    Log.w(TAG, "실내등 $value 실패", it)
                    cont.resume(Unit)
                },
            )
        }
    }

    override fun release() = vehicle.release()

    private companion object {
        const val TAG = "PleosVehicleSignals"
    }
}

private fun VehicleGear?.toGear(): Gear = when {
    this == null -> Gear.OTHER
    name.contains("PARK") -> Gear.PARK
    name.contains("REVERSE") -> Gear.REVERSE
    name.contains("NEUTRAL") -> Gear.NEUTRAL
    name.contains("DRIVE") -> Gear.DRIVE
    else -> Gear.OTHER
}

private fun PassengerDoor.toDoorValue(): DoorValue = when (this) {
    PassengerDoor.ROW_1_RIGHT -> DoorValue.ROW_1_RIGHT
    PassengerDoor.ROW_2_LEFT -> DoorValue.ROW_2_LEFT
    PassengerDoor.ROW_2_RIGHT -> DoorValue.ROW_2_RIGHT
}

private fun LightSwitch.toCabinSwitch(): CabinSwitch = CabinSwitch.valueOf(name)

private fun CabinSwitch.toLightSwitch(): LightSwitch = LightSwitch.valueOf(name)
