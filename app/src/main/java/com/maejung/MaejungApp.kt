package com.maejung

import android.app.Application
import com.maejung.location.AndroidLocationSource
import com.maejung.net.FakeSessionTransport
import com.maejung.session.CarSessionManager
import com.maejung.store.Prefs
import com.maejung.vehicle.PleosVehicleSignals
import com.maejung.voice.PleosVoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 연결과 세션을 들고 있는 곳. 화면 회전·Activity 재생성에 끊기지 않게 Application 범위에 둔다. DI 프레임워크는 쓰지 않는다.
 * 지금(A4)은 서버 대신 FakeSessionTransport로 돈다. A5에서 WebSocketTransport로 바꾼다.
 */
class MaejungApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var fakeTransport: FakeSessionTransport
        private set
    lateinit var sessionManager: CarSessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        fakeTransport = FakeSessionTransport(scope)
        sessionManager = CarSessionManager(
            scope = scope,
            transport = fakeTransport,
            vehicle = PleosVehicleSignals(this),
            location = AndroidLocationSource(this),
            voice = PleosVoiceIO(this),
            prefs = Prefs(this),
        )
        sessionManager.start(token = "fake")
    }
}
