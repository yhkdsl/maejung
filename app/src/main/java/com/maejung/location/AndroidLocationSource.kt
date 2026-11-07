package com.maejung.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * LocationManager GPS 구독. 에뮬레이터에서는 `adb emu geo fix <경도> <위도>`가 바로 들어오고,
 * 정차 중에도 3초마다 같은 좌표가 온다(docs/DESIGN.md 7절). FusedLocation SDK로 바꿀 수 있게 인터페이스 뒤에 둔다.
 */
class AndroidLocationSource(context: Context) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val _fixes = MutableSharedFlow<Fix>(replay = 1, extraBufferCapacity = 16)
    override val fixes: Flow<Fix> = _fixes.asSharedFlow()

    private val listener = LocationListener { location ->
        _fixes.tryEmit(Fix(location.latitude, location.longitude, location.accuracy, location.time))
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        // 퍼미션은 MainActivity가 받는다. 없으면 SecurityException이라 조용히 넘기고 다음 start에서 다시 시도한다
        runCatching {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(listener::onLocationChanged)
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, listener)
        }
    }

    override fun stop() = manager.removeUpdates(listener)

    private companion object {
        const val INTERVAL_MS = 3_000L
    }
}
