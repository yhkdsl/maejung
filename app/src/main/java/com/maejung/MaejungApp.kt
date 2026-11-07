package com.maejung

import android.app.Application
import com.maejung.location.AndroidLocationSource
import com.maejung.net.ApiClient
import com.maejung.net.FakeSessionTransport
import com.maejung.net.WebSocketTransport
import com.maejung.session.CarSessionManager
import com.maejung.store.Prefs
import com.maejung.vehicle.PleosVehicleSignals
import com.maejung.voice.PleosVoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 연결과 세션을 들고 있는 곳. 화면 회전·Activity 재생성에 끊기지 않게 Application 범위에 둔다. DI 프레임워크는 쓰지 않는다.
 * 서버는 BuildConfig.SERVER_URL. `-Pmaejung.fakeServer=true`로 빌드하면 서버 없이 FakeSessionTransport로 돈다.
 */
class MaejungApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 가짜 서버로 돌 때만 있다(홈 화면 디버그 버튼) */
    var fakeTransport: FakeSessionTransport? = null
        private set
    lateinit var sessionManager: CarSessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        val fake = if (BuildConfig.FAKE_SERVER) FakeSessionTransport(scope) else null
        fakeTransport = fake
        sessionManager = CarSessionManager(
            scope = scope,
            transport = fake ?: WebSocketTransport(BuildConfig.SERVER_URL, scope),
            vehicle = PleosVehicleSignals(this),
            location = AndroidLocationSource(this),
            voice = PleosVoiceIO(this),
            prefs = Prefs(this),
            api = if (fake == null) ApiClient(BuildConfig.SERVER_URL) else null,
        )
        sessionManager.start()
    }
}
