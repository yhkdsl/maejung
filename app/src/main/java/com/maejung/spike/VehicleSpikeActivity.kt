package com.maejung.spike

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.EventListener as PleosTtsEventListener
import ai.pleos.playground.tts.TextToSpeech as PleosTextToSpeech
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vehicle SDK 스파이크: 앱이 쓸 도메인의 조회, 구독, 제어와 외부 통신이 에뮬레이터에서 되는지 확인한다.
 * 3·4번(각성·바다 연출, 창문)은 이전 컨셉 "동행"의 검증 기록이다.
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

    private val http = OkHttpClient()
    private lateinit var naviHelper: NaviHelper
    private var tts: TextToSpeech? = null
    private var pleosTts: PleosTextToSpeech? = null
    private val passengerSeats = listOf(SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT).map { SeatArea(it) }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(selection: VehicleGear?) = log("[구독] 기어 = $selection")
        override fun onFailed(e: Exception) = log("[구독] 기어 실패: $e")
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = log("[구독] 브레이크 = $status")
        override fun onFailed(e: Exception) = log("[구독] 브레이크 실패: $e")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vehicle = Vehicle(this)
        vehicle.initialize()
        naviHelper = NaviHelper(applicationContext)
        naviHelper.initialize(
            onConnected = {
                naviHelper.addListener(naviListener)
                log("NaviHelper 연결됨")
            },
            onFailed = { log("NaviHelper 연결 실패: $it") },
        )

        logView = TextView(this).apply { textSize = 14f; setPadding(24, 24, 24, 24) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(button("1. 조회") { readAll() })
            addView(button("2. 구독 시작") { subscribe() })
            addView(button("3. 각성 연출") { awakeCue() })
            addView(button("4. 키즈 바다 연출") { oceanCue() })
            addView(button("5. 캐퍼빌리티 확인") { capabilities() })
            addView(button("6. 외부 통신 (HTTPS, WebSocket)") { network() })
            addView(button("7. 탑승 감지 구독 (기어, 문, 착석, 벨트)") { boarding() })
            addView(button("8. 실내등 켜기") { cabinLight(LightSwitch.ON) })
            addView(button("9. 실내등 끄기") { cabinLight(LightSwitch.OFF) })
            addView(button("10. 위치 받기") { location() })
            addView(button("11. 음성 확인 (TTS, STT)") { voice() })
            addView(button("11-1. Pleos TTS (ON_DEVICE)") { pleosTts() })
            addView(button("12. 탑승자 위치로 길안내") { naviRoute() })
            addView(button("13. 길안내 취소") { naviHelper.cancelRoute(); log("길안내 취소 요청") })
            addView(button("14. 요청 알림 (5초 뒤)") { delayedAlert() })
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
        getSystemService(LocationManager::class.java).removeUpdates(locationListener)
        naviHelper.removeListener(naviListener)
        naviHelper.release()
        tts?.shutdown()
        pleosTts?.release()
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
        // 내기순환은 좌석별이 아니라 차량 전체 영역이라 지원 영역을 받아서 호출한다
        hvac.checkHvacRecirculationCapabilities(
            onComplete = { config, areas ->
                log("내기순환 지원 영역 = ${areas.map { it.values.toList() }}, config = $config")
                areas.forEach { area ->
                    hvac.setHvacRecirculation(
                        area, false,
                        onComplete = { _, v -> log("내기순환 → $v (false = 외기 유입)") },
                        onFailed = { log("내기순환 제어 실패: $it") },
                    )
                }
            },
            onFailed = { log("내기순환 캐퍼빌리티 실패: $it") },
        )
        vehicle.getWindow().checkWindowPositionCapabilities(
            onComplete = { bounds: Map<WindowArea, *> ->
                log("창문 캐퍼빌리티 = $bounds")
            },
            onFailed = { log("창문 캐퍼빌리티 실패: $it") },
        )
        vehicle.getWindow().setWindowPosition(
            // 범위가 0~10(완전 개방)이라 2 = 약 20% 소폭 개방
            driverWindow, 2,
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
        // 앰비언트도 좌석별이 아니라 차량 전체 영역이라 지원 영역을 받아서 호출한다
        val light = vehicle.getLight()
        light.checkAmbientLightBrightnessColorCapabilities(
            onComplete = { _, areas ->
                areas.forEach { area ->
                    light.setAmbientLightSwitch(
                        area, LightSwitch.ON,
                        onComplete = { _, v -> log("앰비언트 스위치 → $v") },
                        onFailed = { log("앰비언트 스위치 실패: $it") },
                    )
                    light.setAmbientLightBrightnessColor(
                        area, color,
                        onComplete = { _, v -> log("앰비언트 색 → #${Integer.toHexString(v)}") },
                        onFailed = { log("앰비언트 색 실패: $it") },
                    )
                }
            },
            onFailed = { log("앰비언트 캐퍼빌리티 실패: $it") },
        )
    }

    /** 마중: 차 앱이 외부 서버와 HTTPS, WebSocket으로 통신할 수 있는지 */
    private fun network() {
        http.newCall(Request.Builder().url("https://httpbin.org/get").build()).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) =
                response.use { log("HTTPS → ${it.code}, ${it.body?.string()?.length}바이트") }
            override fun onFailure(call: Call, e: IOException) = log("HTTPS 실패: $e")
        })
        http.newWebSocket(Request.Builder().url("wss://echo.websocket.org").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                log("WebSocket 연결됨")
                webSocket.send("maejung-ping")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                log("WebSocket 수신: $text")
                if (text == "maejung-ping") webSocket.close(1000, null)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = log("WebSocket 실패: $t")
        })
    }

    /** 마중: 탑승 자동 종료에 쓸 신호(기어 P, 문 열림·닫힘, 착석, 벨트)가 들어오는지 */
    private fun boarding() {
        vehicle.getGear().registerGearSelection(gearListener)
        val door = vehicle.getDoor()
        door.checkDoorPositionCapabilities(
            onComplete = { bounds: Map<DoorArea, *> ->
                log("문 캐퍼빌리티 = $bounds")
                bounds.keys.forEach { area -> door.registerDoorPosition(area, doorListener(area)) }
            },
            onFailed = { log("문 캐퍼빌리티 실패: $it") },
        )
        val seat = vehicle.getSeat()
        passengerSeats.forEach { area ->
            seat.getCurrentSeatOccupancy(
                area,
                onComplete = { log("착석 ${area.values.toList()} = $it") },
                onFailed = { log("착석 ${area.values.toList()} 실패: $it") },
            )
            seat.registerSeatOccupancy(area, object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(occupancy: Boolean?) = log("[구독] 착석 ${area.values.toList()} = $occupancy")
                override fun onFailed(e: Exception) = log("[구독] 착석 실패: $e")
            })
            seat.registerSeatBeltBuckledState(area, object : SeatBeltBuckledStateListener {
                override fun onSeatBeltBuckledStateUpdated(state: Boolean?) = log("[구독] 벨트 ${area.values.toList()} = $state")
                override fun onFailed(e: Exception) = log("[구독] 벨트 실패: $e")
            })
        }
        log("기어, 문, 착석, 벨트 구독 등록")
    }

    private fun doorListener(area: DoorArea) = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) = log("[구독] 문 ${area.values.toList()} = $pos")
        override fun onFailed(e: Exception) = log("[구독] 문 실패: $e")
    }

    /** 마중: 탑승자가 가까워지면 실내등을 켜서 밖에서 차를 찾게 한다. 비상등은 SDK에 API가 없다 */
    private fun cabinLight(value: LightSwitch) {
        val light = vehicle.getLight()
        light.checkCabinLightSwitchCapabilities(
            onComplete = { switches: List<LightSwitch> -> log("실내등 지원 스위치 = $switches") },
            onFailed = { log("실내등 캐퍼빌리티 실패: $it") },
        )
        light.setCabinLightSwitch(
            value,
            onComplete = {
                log("실내등 → $it")
                light.getCabinLightSwitch(onComplete = { s -> log("실내등 스위치 조회 = $s") }, onFailed = { e -> log("실내등 스위치 조회 실패: $e") })
                light.getCurrentCabinLightState(onComplete = { s -> log("실내등 상태 조회 = $s") }, onFailed = { e -> log("실내등 상태 조회 실패: $e") })
            },
            onFailed = { log("실내등 제어 실패: $it") },
        )
    }

    /** C1: 에뮬레이터 `emu geo fix`로 넣은 좌표가 LocationManager로 들어오는지 */
    @SuppressLint("MissingPermission")
    private fun location() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO), 1)
            log("위치 퍼미션 요청")
            return
        }
        val lm = getSystemService(LocationManager::class.java)
        log("위치 제공자 = ${lm.allProviders}, GPS 켜짐 = ${lm.isProviderEnabled(LocationManager.GPS_PROVIDER)}")
        log("마지막 위치 = ${lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { "${it.latitude}, ${it.longitude}" }}")
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 0f, locationListener)
        log("GPS 위치 구독 등록")
    }

    private val locationListener = LocationListener { loc -> log("[구독] 위치 = ${loc.latitude}, ${loc.longitude} (±${loc.accuracy}m)") }

    /** V1: 한국어 TTS가 되는지, 음성 인식기가 있는지 */
    private fun voice() {
        log("음성 인식 가능 = ${SpeechRecognizer.isRecognitionAvailable(this)}")
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                log("TTS 초기화 실패: $status")
                return@TextToSpeech
            }
            val lang = tts?.setLanguage(Locale.KOREAN)
            log("TTS 한국어 설정 결과 = $lang (0 이상이면 지원, -1 데이터 없음, -2 미지원), 엔진 = ${tts?.defaultEngine}")
            tts?.speak("민지가 데리러 와 달래요. 갈까요?", TextToSpeech.QUEUE_FLUSH, null, "maejung-test")
        }
    }

    /** V1: 안드로이드 기본 TTS 엔진이 없을 때 Pleos TTS SDK의 차량 내장 엔진으로 읽는다 */
    private fun pleosTts() {
        val engine = pleosTts ?: PleosTextToSpeech(applicationContext, Mode.ON_DEVICE).also { pleosTts = it }
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                log("Pleos TTS 초기화 성공")
                engine.addEventListener(object : PleosTtsEventListener {
                    // onReady는 발화가 끝날 때마다 다시 오므로 여기서 speak하면 무한 반복된다
                    override fun onReady() = log("[TTS] 준비됨")
                    override fun onStart() = log("[TTS] 재생 시작")
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onDone() = log("[TTS] 재생 완료")
                    override fun onStop() = log("[TTS] 중지")
                    override fun onError(errorType: ErrorType) = log("[TTS] 오류 $errorType")
                })
                engine.setLocale(Locale.KOREA)
                engine.speak("민지가 데리러 와 달래요. 갈까요?")
            }
            override fun onFailure(errorType: ErrorType) = log("Pleos TTS 초기화 실패: $errorType")
        })
    }

    /** N1: 탑승자 좌표(데모의 학원 출입구)를 목적지로 넣으면 남은 거리·시간이 오는지 */
    private fun naviRoute() {
        naviHelper.requestRoute(
            RouteInfo(
                latitude = 37.49830,
                longitude = 127.06280,
                poiName = "민지 위치",
                poiId = "",
                address = "",
                poiSubId = "0",
            ),
        )
        log("길안내 요청: 37.49830, 127.06280")
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = log("[내비] 경로 시작 $routeStartInfo")
        override fun onRoutingCompleted() = log("[내비] 경로 탐색 완료")
        override fun onDrivingInfo(drivingInfo: DrivingInfo) =
            log("[내비] 남은 거리 ${drivingInfo.destination.distance}m, 시간 ${drivingInfo.destination.duration}")
        override fun onRouteCancelled() = log("[내비] 경로 취소됨")
        override fun onError(errorCode: NaviErrorCode) = log("[내비] 오류 $errorCode")
    }

    /** 다른 앱이 앞에 있을 때 헤드업 알림이 뜨는지 보려고 5초 뒤에 띄운다 */
    private fun delayedAlert() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            log("알림 퍼미션 없음")
        }
        log("5초 뒤 요청 알림")
        logView.postDelayed({
            PickupAlert.show(applicationContext, "민지")
            log("요청 알림 표시")
        }, 5000)
    }

    private fun capabilities() {
        vehicle.getLight().checkAmbientLightBrightnessColorCapabilities(
            onComplete = { config, areas -> log("앰비언트 색 지원 영역 = ${areas.map { it.values.toList() }}, config = $config") },
            onFailed = { log("앰비언트 캐퍼빌리티 실패: $it") },
        )
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { onClick() } }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        // 화면이 바쁠 때 uiautomator dump가 실패하므로 logcat에도 남긴다(`adb logcat -s MaejungSpike`)
        Log.i("MaejungSpike", message)
        runOnUiThread { logView.append("$time  $message\n") }
    }
}
