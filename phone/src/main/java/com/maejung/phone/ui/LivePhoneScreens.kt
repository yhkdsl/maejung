package com.maejung.phone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maejung.phone.net.ConnectionState
import com.maejung.phone.session.LivePhoneState
import com.maejung.protocol.EtaSources
import com.maejung.phone.session.SharingInfo

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
    val locations = liveSharingMapLocations(state)
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var footerHeightPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val gutter = if (maxWidth < 360.dp) 22.dp else 28.dp
        val compactHud = maxWidth < 360.dp || density.fontScale > 1.25f
        // AndroidView has no intrinsic measurements. Measure only the surrounding Compose
        // content, then give the map a definite height, growing it into all spare space.
        val surroundingHeight = with(density) { (headerHeightPx + footerHeightPx).toDp() }
        val mapHeight = maxOf(260.dp, maxHeight - surroundingHeight - 36.dp)
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = gutter, vertical = 18.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().onSizeChanged { headerHeightPx = it.height }.padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (info?.lightOn == true) "엄마 차가\n가까이 있어요" else "엄마가\n데리러 와요",
                    style = MaterialTheme.typography.headlineLarge)
                Text("엄마가 요청을 수락했어요.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LiveConnectionNotice(state, onReconnect)
            }
            Box(Modifier.fillMaxWidth().height(mapHeight).clip(RoundedCornerShape(16.dp))) {
                SharingMap(locations, Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("N ↑", style = MaterialTheme.typography.labelSmall)
                        LiveMapKey("엄마 차", true)
                        LiveMapKey("내 위치", false)
                    }
                }
                if (locations.car == null && locations.rider == null) Surface(
                    modifier = Modifier.align(Alignment.Center).padding(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("위치 정보를 기다리고 있어요.", Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(
                Modifier.fillMaxWidth().onSizeChanged { footerHeightPx = it.height }.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveSharingHud(info, compactHud)
                if (info?.etaSeconds != null) Text(
                    when (info.etaSource) {
                        EtaSources.NAVI -> "차 내비게이션에서 보낸 도착예상이에요."
                        EtaSources.ESTIMATE -> "차에서 계산해 보낸 도착예상이에요."
                        else -> "차에서 보낸 도착예상이에요."
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (info?.lightOn) {
                    true -> Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(15.dp)) {
                        Text("실내등을 켰어요\n실내등 켜진 차를 찾아주세요", Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    false -> Text("차의 실내등이 꺼져 있어요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    null -> Text("실내등 정보를 기다리고 있어요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.padding(top = 6.dp)) { SecondaryAction("마중 끝내기", onEnd) }
            }
        }
    }
}

@Composable
private fun LiveMapKey(label: String, car: Boolean) {
    val color = if (car) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LiveSharingHud(info: SharingInfo?, stacked: Boolean) {
    val angle = relativeCarAngle(info?.bearingDegrees, info?.courseDegrees)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (stacked) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveHudLine("거리") { LiveHudValue(info?.distanceMeters?.let { "${it}m" } ?: "—") }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LiveHudLine("이동 기준") { LiveRelativeCompass(angle) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LiveHudLine("도착예상") { LiveHudValue(sharingEtaLabel(info?.etaSeconds)) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveReadout("거리", info?.distanceMeters?.let { "${it}m" } ?: "—", Modifier.weight(1f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("이동 기준", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LiveRelativeCompass(angle)
                }
                LiveReadout("도착예상", sharingEtaLabel(info?.etaSeconds), Modifier.weight(1.1f))
            }
        }
    }
}

@Composable
private fun LiveHudLine(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1.15f)) { content() }
    }
}

@Composable
private fun LiveHudValue(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LiveRelativeCompass(angle: Int?) {
    val navy = MaterialTheme.colorScheme.primary
    val cyan = MaterialTheme.colorScheme.secondary
    val ring = MaterialTheme.colorScheme.outlineVariant
    val description = if (angle == null) "이동 방향 확인 중" else {
        val directions = listOf("앞", "오른쪽 앞", "오른쪽", "오른쪽 뒤", "뒤", "왼쪽 뒤", "왼쪽", "왼쪽 앞")
        "내 이동 방향 기준 ${directions[((angle + 360 + 22) % 360) / 45]}에 엄마 차가 있어요."
    }
    if (angle == null) {
        Text("—", Modifier.clearAndSetSemantics { contentDescription = description },
            style = MaterialTheme.typography.titleMedium, color = navy)
    } else Canvas(Modifier.size(30.dp).semantics { contentDescription = description }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(ring, size.width * .43f, center, style = Stroke(size.width * .04f))
        drawLine(cyan, Offset(center.x, 0f), Offset(center.x, size.height * .18f),
            size.width * .06f, cap = StrokeCap.Round)
        rotate(angle.toFloat(), center) {
            drawPath(Path().apply {
                moveTo(center.x, size.height * .22f)
                lineTo(size.width * .67f, size.height * .76f)
                lineTo(center.x, size.height * .64f)
                lineTo(size.width * .33f, size.height * .76f)
                close()
            }, navy)
        }
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
        state.locationNotice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
