package com.maejung.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.maejung.BuildConfig
import com.maejung.MaejungApp
import com.maejung.net.ConnectionState
import com.maejung.session.CarSession
import kotlinx.coroutines.flow.StateFlow

data class HomeUiState(
    val connected: Boolean = false,
    val serverUrl: String = BuildConfig.SERVER_URL,
    val family: List<String> = emptyList(),
)

/** 세션은 Application 범위의 CarSessionManager가 소유하고 여기서는 구독과 입력 전달만 한다 */
class CarViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MaejungApp
    private val manager = app.sessionManager

    val session: StateFlow<CarSession> = manager.session
    val connection: StateFlow<ConnectionState> = app.fakeTransport.connection

    fun accept() = manager.accept()

    fun reject() = manager.reject()

    fun end() = manager.end()

    fun fakeRequest() = app.fakeTransport.simulateRequest()

    fun restartLocation() = manager.restartLocation()
}
