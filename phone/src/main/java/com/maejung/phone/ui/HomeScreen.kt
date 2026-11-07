package com.maejung.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maejung.phone.session.PhoneState
import com.maejung.phone.session.PreviewScene
import com.maejung.protocol.EndReasons

@Composable
fun HomeScreen(
    state: PhoneState,
    onRequestPreview: () -> Unit,
    onEndPreview: () -> Unit,
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PhonePage(modifier) {
        when {
            state.scene == PreviewScene.REQUESTING -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("요청 대기 화면", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("엄마의 응답을\n기다리고 있어요", style = MaterialTheme.typography.headlineLarge)
                    Text("응답을 기다리는 화면의 예시예요.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    SilverVehicleIllustration(Modifier.fillMaxWidth().height(220.dp))
                    FamilyCarIdentity("요청 대기 · 예시")
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("실제 요청은 전송되지 않았어요. 다음 화면은 상단 미리보기 메뉴에서 선택해 주세요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryAction("요청 대기 미리보기 종료", onEndPreview)
                }
            }
            state.scene.endReason != null -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("종료 문구 미리보기", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(endedMessage(state.scene.endReason), style = MaterialTheme.typography.headlineLarge)
                }
                SilverVehicleIllustration(Modifier.fillMaxWidth().height(200.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("선택한 종료 상황의 예시예요. 실제 연결이나 위치공유가 있었던 것은 아니에요.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryAction("홈 미리보기로", onReturnHome)
                }
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val greeting = state.name.trim().takeIf { it.isNotEmpty() }?.let { "${it}님," } ?: "안녕하세요,"
                    Text("$greeting\n이제 집에 갈까요?", style = MaterialTheme.typography.headlineLarge)
                    Text("익숙한 우리 차로, 편안한 귀가.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    SilverVehicleIllustration(Modifier.fillMaxWidth().height(240.dp))
                    FamilyCarIdentity("예시 차량")
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("차에 요청을 보내지 않는 오프라인 미리보기예요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryAction("데리러 와줘 · 미리보기", onRequestPreview)
                }
            }
        }
    }
}

@Composable
private fun FamilyCarIdentity(status: String) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("엄마 차", fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                Text("우리 가족의 차", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PixelMark(Modifier.size(12.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun endedMessage(reason: String?): String = when (reason) {
    EndReasons.BOARDED -> "탑승 확인! 위치공유를 끝냈어요"
    EndReasons.REJECTED -> "엄마가 지금은 못 온대요"
    EndReasons.MANUAL -> "위치공유를 끝냈어요"
    EndReasons.REQUEST_TIMEOUT -> "응답이 없어요. 다시 요청해 볼까요?"
    EndReasons.PEER_LOST -> "연결이 끊겨서 위치공유를 끝냈어요"
    EndReasons.MAX_DURATION -> "1시간이 지나 위치공유를 끝냈어요"
    else -> "위치공유를 끝냈어요"
}
