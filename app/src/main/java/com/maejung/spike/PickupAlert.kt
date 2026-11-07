package com.maejung.spike

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** 다른 앱(내비 등) 위에 작게 뜨는 픽업 요청 알림. AAOS는 중요도 HIGH 알림을 헤드업으로 보여준다 */
object PickupAlert {
    const val NOTIFICATION_ID = 1001
    const val ACTION_ACCEPT = "com.maejung.ACCEPT"
    const val ACTION_REJECT = "com.maejung.REJECT"
    private const val CHANNEL_ID = "pickup_request"

    fun show(context: Context, riderName: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "픽업 요청", NotificationManager.IMPORTANCE_HIGH),
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${riderName}가 데리러 와 달래요")
            .setContentText("수락하면 위치를 공유하고 길안내를 시작해요")
            // CATEGORY_CALL로 하면 AAOS가 전화 수신처럼 초록·빨강 아이콘 버튼으로 바꿔 그린다
            .setCategory(Notification.CATEGORY_MESSAGE)
            .addAction(action(context, ACTION_ACCEPT, "수락"))
            .addAction(action(context, ACTION_REJECT, "거절"))
            .build()
        // 알림 센터에 이전 요청이 남아 있으면 갱신만 되고 헤드업으로 다시 뜨지 않아서 먼저 지운다
        manager.cancel(NOTIFICATION_ID)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun action(context: Context, action: String, label: String): Notification.Action {
        val intent = Intent(context, PickupAlertReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(null, label, pending).build()
    }
}
