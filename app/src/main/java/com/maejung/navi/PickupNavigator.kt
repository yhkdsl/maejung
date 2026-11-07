package com.maejung.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 길안내로 받은 남은 거리·시간 */
data class NaviEta(val meters: Int, val seconds: Int)

/** 탑승자 위치까지 길안내(docs/CAR_APP.md 3.3 길안내 규칙) */
interface Navigator {
    /** 길안내 중일 때만 값이 있다 */
    val eta: StateFlow<NaviEta?>

    fun start(lat: Double, lng: Double, riderName: String)

    fun stop()
}

/** NaviHelper. `requestRoute`를 부르면 내장 내비 앱이 화면 앞으로 나온다(docs/DESIGN.md 7절) */
class PickupNavigator(context: Context) : Navigator {
    private val navi = NaviHelper(context.applicationContext)
    private val _eta = MutableStateFlow<NaviEta?>(null)
    override val eta: StateFlow<NaviEta?> = _eta.asStateFlow()
    private var active = false

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.i(TAG, "길안내 시작 ${routeStartInfo.distance}m ${routeStartInfo.duration}초")
            if (active) _eta.value = NaviEta(routeStartInfo.distance, routeStartInfo.duration)
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            if (active) _eta.value = NaviEta(drivingInfo.destination.distance, drivingInfo.destination.duration)
        }

        override fun onRouteCancelled() {
            Log.i(TAG, "길안내 취소됨")
            _eta.value = null
        }

        override fun onError(errorCode: NaviErrorCode) = Log.w(TAG, "내비 오류 $errorCode").let { }
    }

    init {
        navi.initialize(
            onConnected = { navi.addListener(listener) },
            onFailed = { Log.w(TAG, "NaviHelper 연결 실패", it) },
        )
    }

    override fun start(lat: Double, lng: Double, riderName: String) {
        active = true
        navi.requestRoute(RouteInfo(latitude = lat, longitude = lng, poiName = "$riderName 위치", poiId = "", address = "", poiSubId = "0"))
    }

    override fun stop() {
        if (!active) return
        active = false
        _eta.value = null
        navi.cancelRoute()
    }

    private companion object {
        const val TAG = "PickupNavigator"
    }
}
