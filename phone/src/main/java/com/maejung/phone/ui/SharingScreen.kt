package com.maejung.phone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maejung.phone.session.SharingInfo

@Composable
fun SharingScreen(info: SharingInfo, onEndPreview: () -> Unit, modifier: Modifier = Modifier) {
    val distance = info.distanceMeters
    val showLocations = distance != null && distance >= 0 && info.bearingDegrees != null
    val nearby = showLocations && distance <= 30
    val relativeAngle = if (showLocations) relativeCarAngle(info.bearingDegrees, info.courseDegrees) else null
    val largeText = LocalDensity.current.fontScale > 1.25f
    var hudHeightPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val viewportHeight = maxHeight
        val narrow = maxWidth <= 360.dp
        val minMapHeight = when {
            nearby && narrow -> 360.dp
            nearby -> 380.dp
            narrow -> 390.dp
            else -> 420.dp
        } + if (largeText) 120.dp else 0.dp
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                // Bound the scroll content before weights so spare height grows the map.
                .height(IntrinsicSize.Min)
                .padding(horizontal = if (narrow) 22.dp else 28.dp, vertical = 16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        !showLocations -> "위치 정보를\n기다리고 있어요"
                        nearby -> "엄마 차가\n가까이 있어요"
                        else -> "엄마 차를\n찾아가요"
                    },
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.semantics { heading() },
                )
                Row(
                    Modifier.padding(top = 12.dp, bottom = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    SharingStatusMark()
                    Text(
                        if (showLocations) "위치 공유 예시" else "위치 정보 없음",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    )
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f).heightIn(min = minMapHeight)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    SharingMap(
                        nearby, showLocations,
                        courseDegrees = info.courseDegrees.takeIf { relativeAngle != null },
                        bottomOverlayHeightPx = hudHeightPx.toFloat(),
                        modifier = Modifier.fillMaxSize(),
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    ) {
                        Text(
                            "N ↑", Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                                .clearAndSetSemantics { contentDescription = "지도 위쪽은 북쪽" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        )
                    }
                    if (!showLocations) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp),
                        ) {
                            Text(
                                "위치 정보가 없어\n차 위치를 표시할 수 없어요.",
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
                            )
                        }
                    }
                    SharingHud(
                        info, showLocations, relativeAngle, largeText,
                        Modifier.align(Alignment.BottomCenter)
                            .onSizeChanged { hudHeightPx = it.height }.padding(12.dp),
                    )
                }
                when (info.lightOn) {
                    true -> CabinLightCue(Modifier.padding(top = 16.dp))
                    null -> Text(
                        "실내등 정보를 확인할 수 없어요.", Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    )
                    false -> Unit
                }
            }
            OutlinedButton(
                onClick = onEndPreview,
                modifier = Modifier.padding(top = 18.dp).fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    "위치 공유 끝내기", Modifier.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
                )
            }
        }
    }
}

@Composable
private fun SharingStatusMark() {
    val navy = MaterialTheme.colorScheme.primary
    val cyan = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.size(12.dp)) {
        val pixel = size.width * .42f
        drawRect(navy, size = Size(pixel, pixel))
        drawRect(cyan, Offset(size.width - pixel, 0f), Size(pixel, pixel))
        drawRect(navy, Offset(size.width - pixel, size.height - pixel), Size(pixel, pixel))
    }
}

@Composable
private fun SharingHud(
    info: SharingInfo,
    showLocations: Boolean,
    relativeAngle: Int?,
    largeText: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = .97f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (largeText) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HudLine("거리") { DistanceReading(info.distanceMeters.takeIf { showLocations }) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HudLine("이동 기준") { RelativeCompass(relativeAngle) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HudLine("도착예상") { EtaReading(info.etaSeconds.takeIf { showLocations }) }
            }
        } else {
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                HudColumn("거리", Modifier.weight(1f)) { DistanceReading(info.distanceMeters.takeIf { showLocations }) }
                HudRule()
                HudColumn("이동 기준", Modifier.weight(1f)) { RelativeCompass(relativeAngle) }
                HudRule()
                HudColumn("도착예상", Modifier.weight(1.1f)) { EtaReading(info.etaSeconds.takeIf { showLocations }) }
            }
        }
    }
}

@Composable
private fun HudColumn(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        HudLabel(label)
        Box(Modifier.heightIn(min = 30.dp), contentAlignment = Alignment.CenterStart) { value() }
    }
}

@Composable
private fun HudLine(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { HudLabel(label) }
        Box(Modifier.weight(1.1f)) { value() }
    }
}

@Composable
private fun HudLabel(label: String) {
    Text(
        label, color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
    )
}

@Composable
private fun HudRule() {
    Surface(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(1.dp, 48.dp)) {}
}

@Composable
private fun DistanceReading(distance: Int?) {
    Row(
        Modifier.clearAndSetSemantics { contentDescription = distance?.let { "거리 ${it}미터 예시" } ?: "거리 정보 없음" },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            distance?.toString() ?: "—", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        )
        if (distance != null) Text(
            "m", Modifier.padding(bottom = 3.dp), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        )
    }
}

@Composable
private fun EtaReading(eta: Int?) {
    Text(
        sharingEtaLabel(eta), color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = if (eta == null || eta < 0) "도착 예상 시간 정보 없음" else "도착예상 ${sharingEtaLabel(eta)} 예시"
        },
    )
}

@Composable
private fun RelativeCompass(angle: Int?) {
    val navy = MaterialTheme.colorScheme.primary
    val cyan = MaterialTheme.colorScheme.secondary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.outlineVariant
    val description = if (angle == null) "이동 방향 확인 중" else {
        val directions = listOf("앞", "오른쪽 앞", "오른쪽", "오른쪽 뒤", "뒤", "왼쪽 뒤", "왼쪽", "왼쪽 앞")
        "내 이동 방향 기준 ${directions[((angle + 360 + 22) % 360) / 45]}에 엄마 차가 있어요. 예시 방향"
    }
    Canvas(Modifier.size(30.dp).semantics { contentDescription = description }) {
        val unit = size.width / 32f
        withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
            drawCircle(ring, 13f, Offset(16f, 16f), style = Stroke(1.2f))
            if (angle == null) {
                drawLine(muted, Offset(11f, 16f), Offset(21f, 16f), 1.6f, cap = StrokeCap.Round)
            } else {
                drawLine(cyan, Offset(16f, 1f), Offset(16f, 6f), 1.8f, cap = StrokeCap.Round)
                rotate(angle.toFloat(), Offset(16f, 16f)) {
                    drawPath(Path().apply {
                        moveTo(16f, 7f); lineTo(21f, 23f); lineTo(16f, 20f); lineTo(11f, 23f); close()
                    }, navy)
                }
            }
        }
    }
}

@Composable
private fun CabinLightCue(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFF6F2EA),
        border = BorderStroke(1.dp, Color(0xFFE8E0D2)),
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(27.dp, 34.dp)) {
                withTransform({ scale(size.width / 30f, size.height / 34f, pivot = Offset.Zero) }) {
                    val amber = Color(0xFF96703A)
                    val lamp = Path().apply {
                        moveTo(7f, 19f); lineTo(7f, 11f)
                        cubicTo(7f, 1f, 23f, 1f, 23f, 11f)
                        lineTo(23f, 19f); close()
                    }
                    drawPath(lamp, Color(0xFFE3B461).copy(alpha = .33f))
                    drawPath(lamp, amber, style = Stroke(1.2f))
                    drawRect(amber, Offset(11f, 10f), Size(3f, 3f))
                    drawRect(amber, Offset(16f, 10f), Size(3f, 3f))
                    drawLine(amber, Offset(10f, 24f), Offset(8f, 29f), 1.4f, cap = StrokeCap.Round)
                    drawLine(amber, Offset(15f, 24f), Offset(15f, 31f), 1.4f, cap = StrokeCap.Round)
                    drawLine(amber, Offset(20f, 24f), Offset(22f, 29f), 1.4f, cap = StrokeCap.Round)
                }
            }
            Text(
                "실내등 켜진 차를 찾아주세요",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
            )
        }
    }
}
