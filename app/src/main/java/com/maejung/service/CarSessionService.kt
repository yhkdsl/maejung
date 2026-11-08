package com.maejung.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.maejung.alert.PickupNotifications

/**
 * 마중 앱이 화면 뒤에 있어도(내비를 보는 중에도) 서버 연결·요청 수신·위치 전송·탑승 감지가 멈추지 않게 프로세스를 붙잡는다.
 * 요청은 언제 올지 몰라서 세션 중에만이 아니라 앱이 켜져 있는 동안 늘 띄운다.
 * Android 14는 앱이 뒤에 있을 때 포그라운드 서비스 시작을 막으므로, 화면이 보이는 MainActivity에서 시작한다.
 */
class CarSessionService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = PickupNotifications(this).ongoing()
        runCatching {
            // 마이크: 앱이 뒤에 있을 때 음성 승인을 들으려면 microphone 타입이 필요하다(없으면 인식 오류 9, 권한 부족)
            // 타입 인자는 API 29부터라 minSdk 28에서도 안전한 ServiceCompat을 쓴다
            ServiceCompat.startForeground(
                this,
                PickupNotifications.ID_ONGOING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or microphoneType(),
            )
        }.onFailure {
            Log.w(TAG, "포그라운드 서비스 시작 실패", it)
            stopSelf()
        }
        return START_STICKY
    }

    private fun microphoneType(): Int =
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CarSessionService"

        /** 위치 퍼미션이 있어야 location 타입 포그라운드 서비스를 띄울 수 있다 */
        fun start(context: Context) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            context.startForegroundService(Intent(context, CarSessionService::class.java))
        }
    }
}
