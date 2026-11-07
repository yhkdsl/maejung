package com.maejung.phone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.maejung.phone.MainActivity
import com.maejung.phone.MaejungPhoneApp
import com.maejung.phone.R
import com.maejung.phone.location.AndroidLocationSource
import com.maejung.phone.net.ConnectionState
import com.maejung.phone.session.PhonePhase

/** A non-sticky, session-bound foreground service owns the only live GPS subscription. */
class LocationShareService : Service() {
    private val phoneApp: MaejungPhoneApp get() = application as MaejungPhoneApp
    private lateinit var controller: LocationServiceController
    internal var requestedSessionId: String? = null
        private set

    override fun onCreate() {
        super.onCreate()
        val manager = phoneApp.sessionManager
        controller = LocationServiceController(
            source = AndroidLocationSource(this),
            isCurrentSharingSession = ::isCurrentSession,
            onFix = { fix ->
                manager.updateOwnLocation(fix)
                phoneApp.refreshLocationNotice()
            },
            onClear = manager::clearOwnLocation,
            onNotice = { message -> phoneApp.refreshLocationNotice(message); stopSharing() },
            onAppClosed = manager::appClosed,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.location_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra(EXTRA_SESSION)
        requestedSessionId = sessionId
        if (sessionId.isNullOrBlank() || !isCurrentSession(sessionId)) {
            stopSharing()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_END) {
            phoneApp.sessionManager.endSession()
            if (!isCurrentSession(sessionId)) stopSharing()
            return START_NOT_STICKY
        }
        val alreadyRunning = controller.sessionId == sessionId
        if (!locationPermissions(this).granted || !gpsEnabled(this) || (!alreadyRunning && !phoneApp.activityResumed)) {
            phoneApp.refreshLocationNotice(when {
                !locationPermissions(this).granted -> LOCATION_PERMISSION_NOTICE
                !gpsEnabled(this) -> LOCATION_GPS_NOTICE
                else -> LOCATION_RESUME_NOTICE
            })
            stopSharing()
            return START_NOT_STICKY
        }
        try {
            // Android 14 checks while-in-use location again here, after the launch race.
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(sessionId),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } catch (_: RuntimeException) {
            phoneApp.refreshLocationNotice(LOCATION_START_NOTICE)
            stopSharing()
            return START_NOT_STICKY
        }
        phoneApp.locationServiceStarted(this, sessionId)
        controller.start(sessionId)
        if (controller.sessionId == null) stopSharing()
        return START_NOT_STICKY
    }

    internal fun stopSharing() {
        controller.stop()
        phoneApp.locationServiceStopped(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        phoneApp.sharingTaskRemoved()
        controller.taskRemoved()
        Log.i(TAG, "onTaskRemoved: session.end(app_closed) attempted (best effort)")
        stopSharing()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        controller.stop()
        phoneApp.locationServiceStopped(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun isCurrentSession(sessionId: String): Boolean {
        val state = phoneApp.sessionManager.state.value
        return state.phase == PhonePhase.SHARING && state.sessionId == sessionId &&
            state.connection != ConnectionState.REPLACED && state.connection != ConnectionState.UNAUTHORIZED
    }

    private fun notification(sessionId: String): Notification {
        val openApp = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end = PendingIntent.getService(this, 1, startIntent(this, sessionId).setAction(ACTION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location_sharing)
            .setContentTitle(getString(R.string.location_notification_title))
            .setContentText(getString(R.string.location_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.location_notification_end), end)
            .build()
    }

    companion object {
        internal const val CHANNEL_ID = "location_sharing"
        private const val NOTIFICATION_ID = 4101
        private const val EXTRA_SESSION = "sharing_session"
        private const val ACTION_END = "com.maejung.phone.END_LOCATION_SHARING"
        private const val TAG = "MaejungLocationService"
        internal fun startIntent(context: Context, sessionId: String): Intent =
            Intent(context, LocationShareService::class.java).putExtra(EXTRA_SESSION, sessionId)
    }
}
