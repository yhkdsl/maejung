package com.maejung.phone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maejung.phone.BuildConfig
import com.maejung.phone.session.PhoneState
import com.maejung.phone.session.PreviewScene

@Composable
fun PhoneApp(
    state: PhoneState,
    onNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPairPreview: () -> Unit,
    onRequestPreview: () -> Unit,
    onEndPreview: () -> Unit,
    onReturnHome: () -> Unit,
    onBack: () -> Unit,
    onSelectPreview: (PreviewScene) -> Unit,
) {
    val previewEnabled = BuildConfig.DEBUG && state.previewEnabled
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable(state.scene.name) { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    BackHandler(enabled = previewEnabled && state.scene != PreviewScene.INITIAL && !menuOpen && !confirmEnd) {
        if (state.scene.isActive) confirmEnd = true else onBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gutter = if (maxWidth < 360.dp) 22.dp else 28.dp
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = gutter, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelMark()
                            Text("마중", modifier = Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.primary,
                                fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(if (previewEnabled) "오프라인" else "준비 중",
                            modifier = Modifier.background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp).semantics {
                                    contentDescription = if (previewEnabled) "오프라인 미리보기 · 실제 연결·요청·위치공유 없음" else "실제 연결 서비스 준비 중"
                                },
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
                        if (previewEnabled) {
                            IconButton(onClick = { focus.clearFocus(); menuOpen = true },
                                modifier = Modifier.size(48.dp).semantics { contentDescription = "오프라인 미리보기 메뉴" }) {
                                val color = MaterialTheme.colorScheme.onSurfaceVariant
                                Canvas(Modifier.size(22.dp)) {
                                    listOf(.23f, .5f, .77f).forEach { y ->
                                        drawCircle(color, radius = size.width * .075f, center = Offset(size.width / 2f, size.height * y))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        // A release build never renders a saved debug scene, even if passed one externally.
        val displayedState = if (previewEnabled) state else state.copy(scene = PreviewScene.INITIAL, previewEnabled = false)
        key(displayedState.scene) {
            when {
                displayedState.scene == PreviewScene.INITIAL -> PairScreen(
                    displayedState, onNameChange, onCodeChange, onPairPreview, Modifier.padding(padding))
                displayedState.sharing != null -> SharingScreen(
                    displayedState.sharing!!, { confirmEnd = true }, Modifier.padding(padding))
                else -> HomeScreen(displayedState, onRequestPreview, { confirmEnd = true }, onReturnHome, Modifier.padding(padding))
            }
        }
    }

    if (previewEnabled && menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text("오프라인 미리보기") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("화면 예시를 선택해요. 실제 연결이나 요청은 하지 않아요.")
                    PreviewScene.entries.forEach { scene ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            onClick = { menuOpen = false; confirmEnd = false; onSelectPreview(scene) },
                        ) { Text(scene.label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuOpen = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
            },
        )
    }
    if (previewEnabled && confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("미리보기를 끝낼까요?") },
            text = { Text("실제 요청이나 위치공유는 진행되지 않았어요.") },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; onEndPreview() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("미리보기 종료")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("계속 보기") }
            },
        )
    }
}
