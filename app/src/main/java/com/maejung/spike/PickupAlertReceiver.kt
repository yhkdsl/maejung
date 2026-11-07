package com.maejung.spike

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** 헤드업 알림의 수락·거절 버튼을 받는다. 스파이크라 로그만 남기고 알림을 닫는다 */
class PickupAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("MaejungSpike", "[알림] 버튼 = ${intent.action}")
        context.getSystemService(NotificationManager::class.java).cancel(PickupAlert.NOTIFICATION_ID)
    }
}
