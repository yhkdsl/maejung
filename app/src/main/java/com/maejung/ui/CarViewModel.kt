package com.maejung.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maejung.MaejungApp
import com.maejung.eta.Eta
import com.maejung.location.Fix
import com.maejung.protocol.PeerLocation
import com.maejung.net.ConnectionState
import com.maejung.protocol.PairingCodeIssued
import com.maejung.session.CarSession
import com.maejung.store.FamilyMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val family: List<FamilyMember> = emptyList(),
    /** 차 화면에 띄운 6자리 연결 코드 */
    val pairingCode: PairingCodeIssued? = null,
    val pairingFailed: Boolean = false,
    val pairedName: String? = null,
)

/** 세션은 Application 범위의 CarSessionManager가 소유하고 여기서는 구독과 입력 전달만 한다 */
class CarViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MaejungApp
    private val manager = app.sessionManager

    val session: StateFlow<CarSession> = manager.session
    val connection: StateFlow<ConnectionState> = manager.connection
    val family: StateFlow<List<FamilyMember>> = manager.family
    val pairedNotice: StateFlow<String?> = manager.pairedNotice
    val eta: StateFlow<Eta?> = manager.eta
    val bearing: StateFlow<Float?> = manager.bearing
    val carFix: StateFlow<Fix?> = manager.carFix
    val peerFix: StateFlow<PeerLocation?> = manager.peerFix
    val isFake: Boolean = app.fakeTransport != null

    private val _pairingCode = MutableStateFlow<PairingCodeIssued?>(null)
    val pairingCode: StateFlow<PairingCodeIssued?> = _pairingCode.asStateFlow()
    private val _pairingFailed = MutableStateFlow(false)
    val pairingFailed: StateFlow<Boolean> = _pairingFailed.asStateFlow()

    fun accept() = manager.accept()

    fun reject() = manager.reject()

    fun end() = manager.end()

    fun fakeRequest() {
        app.fakeTransport?.simulateRequest()
    }

    fun restartLocation() = manager.restartLocation()

    fun requestPairingCode() {
        viewModelScope.launch {
            val issued = manager.issuePairingCode()
            _pairingCode.value = issued
            _pairingFailed.value = issued == null
        }
    }

    /** 코드가 쓰였거나(가족 연결 완료) 만료되면 닫는다 */
    fun closePairingCode() {
        _pairingCode.value = null
        manager.clearPairedNotice()
    }
}
