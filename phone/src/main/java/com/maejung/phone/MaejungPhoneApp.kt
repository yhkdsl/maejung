package com.maejung.phone

import android.app.Application
import com.maejung.phone.net.ApiClient
import com.maejung.phone.net.WebSocketTransport
import com.maejung.phone.session.PhoneSessionManager
import com.maejung.phone.store.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Keeps the live session and socket across Activity recreation. */
class MaejungPhoneApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
        sessionManager
    }
}
