package com.maejung.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.maejung.MainActivity
import com.maejung.session.toSubject

/** 다른 앱(내비 등) 위에 작게 뜨는 알림(docs/CAR_APP.md 2절 헤드업 알림 규칙) */
interface PickupAlerts {
    fun showRequest(riderName: String)

    fun dismissRequest()

    /** 버튼 없는 안내("가까이 왔어요", "탑승 확인") */
    fun notify(text: String)
}

class PickupNotifications(private val context: Context) : PickupAlerts {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL_REQUEST, "픽업 요청", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_STATUS, "마중 안내", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, "마중 대기", NotificationManager.IMPORTANCE_LOW))
    }

    override fun showRequest(riderName: String) {
        // 알림 센터에 이전 요청이 남아 있으면 갱신만 되고 헤드업으로 다시 뜨지 않아서 먼저 지운다
        manager.cancel(ID_REQUEST)
        val notification = Notification.Builder(context, CHANNEL_REQUEST)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${riderName.toSubject()} 데리러 와 달래요")
            .setContentText("수락하면 위치를 공유하고, 멀리 있으면 길안내를 시작해요")
            // CATEGORY_CALL로 하면 AAOS가 전화 수신처럼 아이콘 버튼으로 바꿔 그려 "수락/거절" 글자가 사라진다
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(openApp())
            // AAOS 헤드업은 버튼을 거꾸로 그리고 마지막 것을 강조한다. 수락을 먼저 넣어야 헤드업에서 수락이 오른쪽·강조된다
            // (알림 센터에서는 순서대로 그려져 거절이 강조된다. 운전자는 주로 헤드업을 보므로 헤드업 기준으로 맞춘다)
            .addAction(action(PickupAlertReceiver.ACTION_ACCEPT, "수락"))
            .addAction(action(PickupAlertReceiver.ACTION_REJECT, "거절"))
            .build()
        manager.notify(ID_REQUEST, notification)
    }

    override fun dismissRequest() = manager.cancel(ID_REQUEST)

    override fun notify(text: String) {
        manager.cancel(ID_STATUS)
        val notification = Notification.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(text)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager.notify(ID_STATUS, notification)
    }

    /** 포그라운드 서비스용 상시 알림 */
    fun ongoing(): Notification = Notification.Builder(context, CHANNEL_ONGOING)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("마중이 요청을 기다리고 있어요")
        .setContentIntent(openApp())
        .setOngoing(true)
        .build()

    private fun action(action: String, label: String): Notification.Action {
        val intent = Intent(context, PickupAlertReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(null, label, pending).build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ID_ONGOING = 2000
        private const val ID_REQUEST = 2001
        private const val ID_STATUS = 2002
        private const val CHANNEL_REQUEST = "pickup_request"
        private const val CHANNEL_STATUS = "pickup_status"
        private const val CHANNEL_ONGOING = "pickup_ongoing"
    }
}
