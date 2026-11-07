package com.maejung.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maejung.net.ConnectionState
import com.maejung.session.toSubject
import kotlinx.coroutines.delay

/** 연결 상태, 연결된 가족, 가족 연결 코드. 운전 중에는 이 화면을 볼 일이 거의 없다(요청은 헤드업 알림으로 온다) */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPairClick: () -> Unit,
    onPairingClose: () -> Unit,
    onFakeRequest: (() -> Unit)? = null,
) {
    // 가족 연결이 끝나면 3초 보여 주고 코드를 닫는다
    LaunchedEffect(state.pairedName) {
        if (state.pairedName != null) {
            delay(3_000)
            onPairingClose()
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("마중", fontSize = 56.sp, style = MaterialTheme.typography.headlineLarge)
        Text(state.connection.label(), fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (state.family.isEmpty()) "연결된 가족이 없어요" else "연결된 가족: ${state.family.joinToString { it.riderName }}",
            fontSize = 32.sp,
        )
        when {
            state.pairedName != null -> Text("${state.pairedName.toSubject()} 연결됐어요", fontSize = 48.sp, color = MaterialTheme.colorScheme.primary)
            state.pairingCode != null -> {
                Text("폰의 마중 앱에 이 코드를 입력하세요 (10분 동안 유효)", fontSize = 28.sp)
                Text(state.pairingCode.code.chunked(3).joinToString(" "), fontSize = 96.sp, color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = onPairingClose, modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    Text("닫기", fontSize = 32.sp)
                }
            }
            else -> {
                if (state.pairingFailed) Text("코드를 받지 못했어요. 서버 연결을 확인해 주세요", fontSize = 28.sp, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = onPairClick,
                    enabled = state.connection == ConnectionState.CONNECTED && onFakeRequest == null,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                ) {
                    Text("가족 연결", fontSize = 36.sp)
                }
            }
        }
        // 가짜 서버로 돌 때만 보이는 디버그 버튼(A4)
        if (onFakeRequest != null) {
            OutlinedButton(onClick = onFakeRequest, modifier = Modifier.fillMaxWidth().height(120.dp)) {
                Text("가짜 요청 받기 (디버그)", fontSize = 32.sp)
            }
        }
    }
}

private fun ConnectionState.label() = when (this) {
    ConnectionState.CONNECTED -> "서버 연결됨"
    ConnectionState.CONNECTING -> "서버에 연결하는 중"
    ConnectionState.DISCONNECTED -> "서버 연결이 끊겼어요. 다시 연결하는 중"
    ConnectionState.UNAUTHORIZED -> "차를 다시 등록하는 중"
    ConnectionState.REPLACED -> "다른 곳에서 이 차로 연결했어요"
}
