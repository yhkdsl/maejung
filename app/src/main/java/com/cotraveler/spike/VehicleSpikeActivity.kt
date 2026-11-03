package com.cotraveler.spike

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vehicle SDK 스파이크: 동행 앱이 쓸 도메인의 조회, 구독, 제어가 에뮬레이터에서 되는지 확인한다.
 * Virtual Console로 신호를 바꿔가며 로그를 본다.
 */
class VehicleSpikeActivity : Activity() {

    private lateinit var vehicle: Vehicle
    private lateinit var logView: TextView

    private val driver = SeatArea(SeatValue.ROW_1_LEFT)
    private val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)

    private val drivingTimeListener = object : DrivingTimeListener {
        override fun onDrivingTimeUpdated(value: Int?) = log("[구독] 트립 주행시간 = ${value}분")
        override fun onFailed(e: Exception) = log("[구독] 트립 주행시간 실패: $e")
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = log("[구독] 브레이크 = $status")
        override fun onFailed(e: Exception) = log("[구독] 브레이크 실패: $e")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vehicle = Vehicle(this)
        vehicle.initialize()

        logView = TextView(this).apply { textSize = 14f; setPadding(24, 24, 24, 24) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(button("1. 조회") { readAll() })
            addView(button("2. 구독 시작") { subscribe() })
            addView(button("3. 각성 연출") { awakeCue() })
            addView(button("4. 키즈 바다 연출") { oceanCue() })
            addView(button("5. 캐퍼빌리티 확인") { capabilities() })
            addView(button("로그 지우기") { logView.text = "" })
            addView(ScrollView(context).apply { addView(logView) })
        }
        setContentView(root)
        log("Vehicle SDK initialize() 호출")
    }

    override fun onDestroy() {
        vehicle.getOdometer().unregisterTripCurrentDrivingTime(drivingTimeListener)
        vehicle.getBrake().unregisterBrakePedalStatus(brakeListener)
        vehicle.release()
        super.onDestroy()
    }

    private fun readAll() {
        val odometer = vehicle.getOdometer()
        odometer.getTripCurrentDrivingTime(
            onComplete = { log("트립 주행시간 = ${it}분") },
            onFailed = { log("트립 주행시간 실패: $it") },
        )
        odometer.getTripCurrentDrivingDistance(
            onComplete = { log("트립 주행거리 = ${it}m") },
            onFailed = { log("트립 주행거리 실패: $it") },
        )
        vehicle.getBrake().getCurrentBrakePedalStatus(
            onComplete = { log("브레이크 밟음 = $it") },
            onFailed = { log("브레이크 실패: $it") },
        )
        vehicle.getDrivingMode().getCruiseControlState(
            onComplete = { log("크루즈 = $it") },
            onFailed = { log("크루즈 실패: $it") },
        )
        vehicle.getHVAC().getHvacTemperature(
            driver, TemperatureUnit.CELSIUS,
            onComplete = { log("운전석 설정온도 = ${it}°C") },
            onFailed = { log("설정온도 실패: $it") },
        )
        vehicle.getWindow().getCurrentWindowPosition(
            driverWindow,
            onComplete = { log("운전석 창문 위치 = $it") },
            onFailed = { log("창문 위치 실패: $it") },
        )
    }

    private fun subscribe() {
        vehicle.getOdometer().registerTripCurrentDrivingTime(drivingTimeListener)
        vehicle.getBrake().registerBrakePedalStatus(brakeListener)
        log("트립 주행시간, 브레이크 구독 등록")
    }

    /** 졸음 각성: 앰비언트 청록, 냉풍 18°C, 외기 유입, 운전석 창문 소폭 개방 */
    private fun awakeCue() {
        ambient(Color.rgb(0, 255, 200))
        val hvac = vehicle.getHVAC()
        hvac.setHvacTemperature(
            driver, 18f, TemperatureUnit.CELSIUS,
            onComplete = { _, v -> log("설정온도 → ${v}°C") },
            onFailed = { log("설정온도 제어 실패: $it") },
        )
        hvac.setHvacRecirculation(
            driver, false,
            onComplete = { _, v -> log("내기순환 → $v (false = 외기 유입)") },
            onFailed = { log("내기순환 제어 실패: $it") },
        )
        vehicle.getWindow().checkWindowPositionCapabilities(
            onComplete = { bounds: Map<WindowArea, *> ->
                log("창문 캐퍼빌리티 = $bounds")
            },
            onFailed = { log("창문 캐퍼빌리티 실패: $it") },
        )
        vehicle.getWindow().setWindowPosition(
            driverWindow, 10,
            onComplete = { _, v -> log("운전석 창문 → $v") },
            onFailed = { log("창문 제어 실패: $it") },
        )
    }

    /** 키즈 스토리 '바다' 장면: 앰비언트 파랑, 23°C */
    private fun oceanCue() {
        ambient(Color.rgb(0, 90, 255))
        vehicle.getHVAC().setHvacTemperature(
            driver, 23f, TemperatureUnit.CELSIUS,
            onComplete = { _, v -> log("설정온도 → ${v}°C") },
            onFailed = { log("설정온도 제어 실패: $it") },
        )
    }

    private fun ambient(color: Int) {
        val light = vehicle.getLight()
        light.setAmbientLightSwitch(
            driver, LightSwitch.ON,
            onComplete = { _, v -> log("앰비언트 스위치 → $v") },
            onFailed = { log("앰비언트 스위치 실패: $it") },
        )
        light.setAmbientLightBrightnessColor(
            driver, color,
            onComplete = { _, v -> log("앰비언트 색 → #${Integer.toHexString(v)}") },
            onFailed = { log("앰비언트 색 실패: $it") },
        )
    }

    private fun capabilities() {
        vehicle.getLight().checkAmbientLightBrightnessColorCapabilities(
            onComplete = { config, areas -> log("앰비언트 색 지원 영역 = $areas, config = $config") },
            onFailed = { log("앰비언트 캐퍼빌리티 실패: $it") },
        )
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { onClick() } }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        runOnUiThread { logView.append("$time  $message\n") }
    }
}
