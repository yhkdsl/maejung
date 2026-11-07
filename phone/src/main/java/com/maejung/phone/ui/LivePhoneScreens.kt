package com.maejung.phone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maejung.phone.net.ConnectionState
import com.maejung.phone.session.LivePhoneState
import com.maejung.protocol.EtaSources
import java.util.Locale

@Composable
internal fun LiveHomeScreen(state: LivePhoneState, onRequest: () -> Unit, onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    PhonePage(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val name = state.pairing?.riderName.orEmpty()
            Text("${if (name.isBlank()) "안녕하세요," else "${name}님,"}\n이제 집에 갈까요?",
                style = MaterialTheme.typography.headlineLarge)
            Text("익숙한 우리 차로, 편안한 귀가.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LiveConnectionNotice(state, onReconnect)
        }
        Column {
            SilverVehicleIllustration(Modifier.fillMaxWidth().height(240.dp))
            FamilyCarIdentity("연결된 차량")
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("엄마 차에 데리러 와 달라고 알려요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryAction(
                label = if (state.connection == ConnectionState.CONNECTING ||
                    (state.connection == ConnectionState.CONNECTED && !state.synchronized)) "연결 중…" else "데리러 와줘",
                onClick = onRequest, enabled = state.canRequest,
            )
        }
    }
}

@Composable
internal fun LiveRequestingScreen(state: LivePhoneState, onEnd: () -> Unit, onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    PhonePage(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("엄마의 응답을\n기다리고 있어요", style = MaterialTheme.typography.headlineLarge)
            Text(if (state.sessionId == null) "요청을 보내고 있어요." else "엄마 차에 알렸어요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LiveConnectionNotice(state, onReconnect)
        }
        Column {
            SilverVehicleIllustration(Modifier.fillMaxWidth().height(220.dp))
            FamilyCarIdentity("응답 대기")
        }
        SecondaryAction("요청 취소", onEnd)
    }
}

@Composable
internal fun LiveSharingScreen(state: LivePhoneState, onEnd: () -> Unit, onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    val info = liveSharingInfo(state)
    PhonePage(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("엄마가\n데리러 와요", style = MaterialTheme.typography.headlineLarge)
            Text("엄마가 요청을 수락했어요.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LiveConnectionNotice(state, onReconnect)
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Deliberately independent of SharingMap: no fictional streets, locations or markers.
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("지도 안내 준비 중", style = MaterialTheme.typography.titleLarge)
                        Text("현재 내 위치는 전송하지 않아요. 지도와 방향 안내는 아직 제공하지 않아요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val location = state.peerLocation
                        if (location == null) {
                            Text("차에서 보낸 위치를 기다리고 있어요.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("차에서 받은 위치", style = MaterialTheme.typography.labelMedium)
                            Text(String.format(Locale.ROOT, "%.5f, %.5f", location.lat, location.lng),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiveReadout("차가 보낸\n경로 거리", info?.distanceMeters?.let { "${it}m" } ?: "—", Modifier.weight(1f))
                    LiveReadout("이동 기준\n나침반", "—", Modifier.weight(1f).semantics {
                        contentDescription = "이동 방향 정보 없음 · 방향 안내를 제공하지 않아요"
                    })
                    LiveReadout("도착예상", sharingEtaLabel(info?.etaSeconds), Modifier.weight(1f))
                }
            }
            if (info?.etaSeconds != null) Text(
                when (info.etaSource) {
                    EtaSources.NAVI -> "차 내비게이션에서 보낸 도착예상이에요."
                    EtaSources.ESTIMATE -> "차에서 계산해 보낸 도착예상이에요."
                    else -> "차에서 보낸 도착예상이에요."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (info?.lightOn) {
                true -> Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(15.dp)) {
                    Text("실내등 켜진 차를 찾아주세요", Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                false -> Text("차의 실내등이 꺼져 있어요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                null -> Text("실내등 정보를 기다리고 있어요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SecondaryAction("마중 끝내기", onEnd)
    }
}

@Composable
internal fun LiveEndedScreen(state: LivePhoneState, onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    PhonePage(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(endedMessage(state.endReason), style = MaterialTheme.typography.headlineLarge)
            LiveConnectionNotice(state, onReconnect)
        }
        SilverVehicleIllustration(Modifier.fillMaxWidth().height(200.dp))
        // The manager owns the three-second transition; the UI never runs a competing timer.
        Text("잠시 후 홈으로 돌아가요.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LiveReadout(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LiveConnectionNotice(state: LivePhoneState, onReconnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val notice = when (state.connection) {
            ConnectionState.CONNECTING -> "엄마 차와 연결하고 있어요."
            ConnectionState.CONNECTED -> if (!state.synchronized) "현재 요청 상태를 확인하고 있어요." else null
            ConnectionState.DISCONNECTED -> "연결이 끊겼어요. 연결 상태를 확인해 주세요."
            ConnectionState.UNAUTHORIZED -> "연결 정보를 확인할 수 없어요. 새 코드로 다시 연결해 주세요."
            ConnectionState.REPLACED -> "다른 연결이 이 연결을 대신했어요. 이 폰에서 계속하려면 다시 연결해 주세요."
        }
        if (notice != null) Text(notice, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.error != null) Text(state.error, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error)
        if (canReconnectLivePhone(state)) SecondaryAction("다시 연결", onReconnect)
    }
}

internal fun liveConnectionLabel(state: LivePhoneState): String = when {
    state.pairingInProgress -> "연결 중"
    state.connection == ConnectionState.UNAUTHORIZED -> "다시 연결 필요"
    state.pairing == null -> "연결 전"
    else -> when (state.connection) {
        ConnectionState.CONNECTING -> "연결 중"
        ConnectionState.CONNECTED -> if (state.synchronized) "연결됨" else "상태 확인 중"
        ConnectionState.DISCONNECTED -> "연결 끊김"
        ConnectionState.UNAUTHORIZED -> "다시 연결 필요"
        ConnectionState.REPLACED -> "다른 연결로 대체됨"
    }
}
