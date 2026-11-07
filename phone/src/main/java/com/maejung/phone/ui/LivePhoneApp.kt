package com.maejung.phone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maejung.phone.BuildConfig
import com.maejung.phone.session.LivePhoneState
import com.maejung.phone.session.PhonePhase
import com.maejung.phone.session.PhoneState

/** Session ownership stays in Application; this root only observes and dispatches UI actions. */
@Composable
fun LivePhoneApp(
    state: LivePhoneState,
    onPair: (code: String, riderName: String) -> Unit,
    onRequestPickup: () -> Unit,
    onEndSession: () -> Unit,
    onReconnect: () -> Unit,
    previewViewModel: PhoneViewModel,
) {
    val latestLive = rememberUpdatedState(state)
    val latestPair = rememberUpdatedState(onPair)
    val latestRequest = rememberUpdatedState(onRequestPickup)
    val latestEnd = rememberUpdatedState(onEndSession)
    val latestReconnect = rememberUpdatedState(onReconnect)
    val createController = { name: String, code: String, validation: Boolean ->
        LivePhoneController(
            liveState = { latestLive.value }, previewEnabled = BuildConfig.DEBUG,
            onPair = { enteredCode, enteredName -> latestPair.value(enteredCode, enteredName) },
            onRequestPickup = { latestRequest.value() }, onEndSession = { latestEnd.value() },
            onReconnect = { latestReconnect.value() },
            initialName = name, initialCode = code, validationShown = validation,
        )
    }
    val controllerSaver = remember {
        Saver<LivePhoneController, List<Any>>(
            // Never save the manager, pairing/token, or preview mode.
            save = { listOf(it.state.value.name, it.state.value.code, it.state.value.validationShown) },
            restore = { createController(it[0] as String, it[1] as String, it[2] as Boolean) },
        )
    }
    val controller = rememberSaveable(saver = controllerSaver) { createController("", "", false) }
    val ui by controller.state.collectAsStateWithLifecycle()
    SideEffect { controller.refreshLiveState() }

    // This guard applies in this frame, before a state-observation effect can run.
    val preview = BuildConfig.DEBUG && ui.mode == PhoneUiMode.PREVIEW &&
        !state.isActive && !state.pairingInProgress
    if (preview) {
        val previewState by previewViewModel.state.collectAsStateWithLifecycle()
        PhoneApp(
            state = previewState,
            onNameChange = previewViewModel::updateName, onCodeChange = previewViewModel::updateCode,
            onPairPreview = previewViewModel::previewPairing,
            onRequestPreview = previewViewModel::previewRequest, onEndPreview = previewViewModel::endPreview,
            onReturnHome = previewViewModel::returnHome, onBack = previewViewModel::navigateBack,
            onSelectPreview = previewViewModel::showPreview, onReturnToLive = controller::returnToLive,
        )
        return
    }

    var menuOpen by remember { mutableStateOf(false) }
    var confirmEnd by rememberSaveable(state.phase, state.sessionId) { mutableStateOf(false) }
    val previewAvailable = BuildConfig.DEBUG && !state.isActive && !state.pairingInProgress
    SideEffect { if (!previewAvailable) menuOpen = false }
    BackHandler(enabled = state.isActive && !confirmEnd) { confirmEnd = true }

    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LivePhoneTopBar(state, previewAvailable) { menuOpen = true }
        },
    ) { padding ->
        key(state.phase) {
            when (livePhoneScreen(state)) {
                LivePhoneScreen.PAIR -> PairScreen(
                    state = PhoneState(name = ui.name, code = ui.code, validationShown = ui.validationShown),
                    onNameChange = controller::updateName, onCodeChange = controller::updateCode,
                    onPreview = controller::pair, modifier = Modifier.padding(padding),
                    live = true, pairingInProgress = state.pairingInProgress, connectionError = state.error,
                )
                LivePhoneScreen.HOME -> LiveHomeScreen(state, controller::requestPickup,
                    controller::reconnect, Modifier.padding(padding))
                LivePhoneScreen.REQUESTING -> LiveRequestingScreen(state, { confirmEnd = true },
                    controller::reconnect, Modifier.padding(padding))
                LivePhoneScreen.SHARING -> LiveSharingScreen(state, { confirmEnd = true },
                    controller::reconnect, Modifier.padding(padding))
                LivePhoneScreen.ENDED -> LiveEndedScreen(state, controller::reconnect, Modifier.padding(padding))
            }
        }
    }

    if (menuOpen && previewAvailable) AlertDialog(
        onDismissRequest = { menuOpen = false },
        title = { Text("오프라인 미리보기") },
        text = { Text("실제 연결 화면과 별도로 예시 화면을 살펴봐요. 미리보기에서는 차에 요청을 보내지 않아요.") },
        confirmButton = {
            TextButton(onClick = { menuOpen = false; controller.enterPreview() },
                modifier = Modifier.heightIn(min = 48.dp)) { Text("미리보기 열기") }
        },
        dismissButton = {
            TextButton(onClick = { menuOpen = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
        },
    )
    if (confirmEnd && state.isActive) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        title = { Text(if (state.phase == PhonePhase.REQUESTING) "요청을 취소할까요?" else "마중을 끝낼까요?") },
        text = { Text(if (state.phase == PhonePhase.REQUESTING) "엄마 차에 보낸 요청을 취소해요." else "진행 중인 마중을 끝내요.") },
        confirmButton = {
            TextButton(onClick = { confirmEnd = false; controller.endSession() },
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (state.phase == PhonePhase.REQUESTING) "요청 취소" else "마중 끝내기")
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmEnd = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("계속하기") }
        },
    )
}

@Composable
private fun LivePhoneTopBar(state: LivePhoneState, previewAvailable: Boolean, onPreview: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gutter = if (maxWidth < 360.dp) 22.dp else 28.dp
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = gutter, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelMark()
                    Text("마중", Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.primary,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                }
                Spacer(Modifier.weight(1f))
                val label = liveConnectionLabel(state)
                Text(label,
                    modifier = Modifier.background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                        .semantics { contentDescription = "실제 연결 상태 · $label" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
                if (BuildConfig.DEBUG) IconButton(onClick = onPreview, enabled = previewAvailable,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = if (previewAvailable) "오프라인 미리보기 열기" else "실제 연결 진행 중 · 미리보기 사용 불가"
                    }) {
                    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (previewAvailable) 1f else .35f)
                    Canvas(Modifier.size(22.dp)) {
                        listOf(.23f, .5f, .77f).forEach { y ->
                            drawCircle(color, size.width * .075f, Offset(size.width / 2f, size.height * y))
                        }
                    }
                }
            }
        }
    }
}
