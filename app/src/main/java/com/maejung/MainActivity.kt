package com.maejung

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maejung.service.CarSessionService
import com.maejung.session.Phase
import com.maejung.ui.CarViewModel
import com.maejung.ui.EndedScreen
import com.maejung.ui.HomeScreen
import com.maejung.ui.HomeUiState
import com.maejung.ui.RequestScreen
import com.maejung.ui.SharingScreen
import com.maejung.vehicle.CabinSwitch

class MainActivity : ComponentActivity() {
    private val viewModel: CarViewModel by viewModels()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.restartLocation()
        CarSessionService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val needed = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray())
        // 화면이 보일 때 시작해야 한다(Android 14는 앱이 뒤에 있을 때 포그라운드 서비스 시작을 막는다)
        CarSessionService.start(this)

        setContent {
            // 주행 중 화면이라 다크 배경을 기본으로 한다
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    val session by viewModel.session.collectAsStateWithLifecycle()
                    val connection by viewModel.connection.collectAsStateWithLifecycle()
                    val family by viewModel.family.collectAsStateWithLifecycle()
                    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
                    val pairingFailed by viewModel.pairingFailed.collectAsStateWithLifecycle()
                    val pairedName by viewModel.pairedNotice.collectAsStateWithLifecycle()
                    when (val phase = session.phase) {
                        Phase.Idle -> HomeScreen(
                            state = HomeUiState(connection, family, pairingCode, pairingFailed, pairedName),
                            onPairClick = viewModel::requestPairingCode,
                            onPairingClose = viewModel::closePairingCode,
                            onFakeRequest = if (viewModel.isFake) viewModel::fakeRequest else null,
                        )
                        is Phase.Requested -> RequestScreen(phase.riderName, viewModel::accept, viewModel::reject)
                        is Phase.Sharing -> SharingScreen(phase.riderName, session.distanceM, nearby = false, lightOn = false, viewModel::end)
                        is Phase.Nearby -> SharingScreen(
                            phase.riderName,
                            session.distanceM,
                            nearby = true,
                            lightOn = session.lightInfo?.supportsOn == true && session.lightBefore != CabinSwitch.ON,
                            onEnd = viewModel::end,
                        )
                        is Phase.Ended -> EndedScreen(phase.reason)
                    }
                }
            }
        }
    }
}
