package com.maejung.phone

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.maejung.phone.net.ApiClient
import com.maejung.phone.net.ConnectionState
import com.maejung.phone.net.WebSocketTransport
import com.maejung.phone.service.LOCATION_PERMISSION_NOTICE
import com.maejung.phone.service.LOCATION_START_NOTICE
import com.maejung.phone.service.LocationServiceAction
import com.maejung.phone.service.LocationShareService
import com.maejung.phone.service.gpsEnabled
import com.maejung.phone.service.locationPermissions
import com.maejung.phone.service.locationServiceDecision
import com.maejung.phone.service.sharingLocationNotice
import com.maejung.phone.session.PhoneSessionManager
import com.maejung.phone.session.PhonePhase
import com.maejung.phone.store.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the live session and socket across Activity recreation. */
class MaejungPhoneApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var locationService: LocationShareService? = null
    private var runningLocationSessionId: String? = null
    private var pendingLocationSessionId: String? = null
    private var taskRemoved = false
    var activityResumed: Boolean = false
        private set
    val sessionManager: PhoneSessionManager by lazy {
        PhoneSessionManager(
            api = ApiClient(BuildConfig.SERVER_URL),
            store = Prefs(this),
            transport = WebSocketTransport(BuildConfig.SERVER_URL, scope),
            scope = scope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            sessionManager.state.map { Triple(it.phase, it.sessionId, it.connection) }
                .distinctUntilChanged().collect { reconcileLocationSharing() }
        }
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { reconcileLocationSharing() }
        }, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Called by Activity resume/pause, never by preview state. */
    fun setActivityResumed(resumed: Boolean) {
        activityResumed = resumed
        if (resumed) {
            taskRemoved = false
            val state = sessionManager.state.value
            if (state.pairing != null && state.connection == ConnectionState.DISCONNECTED) sessionManager.reconnect()
        }
        reconcileLocationSharing()
    }

    /** Rechecks the current session and while-in-use prerequisites on every retry. */
    fun reconcileLocationSharing() {
        if (taskRemoved) {
            stopLocationSharing()
            return
        }
        val state = sessionManager.state.value
        val decision = locationServiceDecision(
            phase = state.phase,
            sessionId = state.sessionId,
            permissions = locationPermissions(this),
            gpsEnabled = gpsEnabled(this),
            activityResumed = activityResumed,
            runningSessionId = runningLocationSessionId,
            connection = state.connection,
        )
        when (decision.action) {
            LocationServiceAction.STOP, LocationServiceAction.WAIT -> {
                stopLocationSharing()
                if (state.phase == PhonePhase.SHARING && decision.notice != null) {
                    refreshLocationNotice(decision.notice)
                } else sessionManager.setLocationNotice(
                    if (state.phase == PhonePhase.REQUESTING && !locationPermissions(this).granted) {
                        LOCATION_PERMISSION_NOTICE
                    } else decision.notice,
                )
            }
            LocationServiceAction.KEEP -> {
                refreshLocationNotice()
            }
            LocationServiceAction.START -> {
                val sessionId = state.sessionId ?: return
                if (pendingLocationSessionId == sessionId) {
                    refreshLocationNotice()
                    return
                }
                stopLocationSharing()
                pendingLocationSessionId = sessionId
                refreshLocationNotice()
                try {
                    ContextCompat.startForegroundService(this, LocationShareService.startIntent(this, sessionId))
                } catch (_: RuntimeException) {
                    pendingLocationSessionId = null
                    refreshLocationNotice(LOCATION_START_NOTICE)
                }
            }
        }
    }

    internal fun locationServiceStarted(service: LocationShareService, sessionId: String) {
        locationService = service
        runningLocationSessionId = sessionId
        pendingLocationSessionId = null
        refreshLocationNotice()
    }

    internal fun locationServiceStopped(service: LocationShareService) {
        if (locationService === service) {
            locationService = null
            runningLocationSessionId = null
        }
        if (pendingLocationSessionId == service.requestedSessionId) pendingLocationSessionId = null
    }

    internal fun sharingTaskRemoved() {
        activityResumed = false
        taskRemoved = true
        pendingLocationSessionId = null
    }

    internal fun refreshLocationNotice(primaryNotice: String? = null) {
        val state = sessionManager.state.value
        if (state.phase != PhonePhase.SHARING) return
        val notifications = NotificationManagerCompat.from(this)
        val channelBlocked = notifications.getNotificationChannel(LocationShareService.CHANNEL_ID)?.importance ==
            android.app.NotificationManager.IMPORTANCE_NONE
        sessionManager.setLocationNotice(
            sharingLocationNotice(
                permissions = locationPermissions(this),
                hasOwnLocation = state.ownLocation != null,
                notificationsEnabled = notifications.areNotificationsEnabled() && !channelBlocked,
                primaryNotice = primaryNotice,
            ),
        )
    }

    private fun stopLocationSharing() {
        val service = locationService
        val pending = pendingLocationSessionId != null
        pendingLocationSessionId = null
        if (service != null) service.stopSharing()
        else if (pending) stopService(Intent(this, LocationShareService::class.java))
        if (sessionManager.state.value.ownLocation != null) sessionManager.clearOwnLocation()
    }
}
