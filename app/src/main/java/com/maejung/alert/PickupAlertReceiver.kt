package com.maejung.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maejung.MaejungApp

/** 요청 알림의 수락·거절 버튼 → 세션 매니저 */
class PickupAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = (context.applicationContext as MaejungApp).sessionManager
        when (intent.action) {
            ACTION_ACCEPT -> manager.accept()
            ACTION_REJECT -> manager.reject()
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.maejung.action.ACCEPT"
        const val ACTION_REJECT = "com.maejung.action.REJECT"
    }
}
