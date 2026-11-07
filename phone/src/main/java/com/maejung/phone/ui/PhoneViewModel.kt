package com.maejung.phone.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.maejung.phone.session.PhoneState
import com.maejung.phone.session.PreviewScene
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** P2 presentation only. No token, session manager, socket, GPS or service lifecycle. */
class PhoneViewModel(
    private val savedStateHandle: SavedStateHandle,
    previewEnabled: Boolean,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        PhoneState(
            name = savedStateHandle["name"] ?: "",
            code = savedStateHandle["code"] ?: "",
            validationShown = savedStateHandle["validationShown"] ?: false,
            scene = if (previewEnabled) {
                PreviewScene.entries.firstOrNull { it.name == savedStateHandle.get<String>("scene") }
                    ?: PreviewScene.INITIAL
            } else PreviewScene.INITIAL,
            previewEnabled = previewEnabled,
        ),
    )
    val state: StateFlow<PhoneState> = mutableState.asStateFlow()

    fun updateName(value: String) = update(state.value.copy(name = value))
    fun updateCode(value: String) = update(state.value.copy(code = value))

    fun previewPairing() {
        val current = state.value
        if (!current.previewEnabled || current.scene != PreviewScene.INITIAL) return
        update(current.copy(
            validationShown = true,
            scene = if (current.canPreviewPairing) PreviewScene.HOME else PreviewScene.INITIAL,
        ))
    }

    /** Only the explicitly labelled debug menu can jump directly to a sample scenario. */
    fun showPreview(scene: PreviewScene) {
        if (state.value.previewEnabled) update(state.value.copy(scene = scene))
    }

    fun previewRequest() {
        if (state.value.previewEnabled && state.value.scene == PreviewScene.HOME) {
            update(state.value.copy(scene = PreviewScene.REQUESTING))
        }
    }

    fun endPreview() {
        if (state.value.previewEnabled && state.value.scene.isActive) {
            update(state.value.copy(scene = PreviewScene.ENDED_MANUAL))
        }
    }

    fun returnHome() {
        if (state.value.previewEnabled && state.value.scene.endReason != null) {
            update(state.value.copy(scene = PreviewScene.HOME))
        }
    }

    fun navigateBack() {
        if (!state.value.previewEnabled) return
        when {
            state.value.scene == PreviewScene.HOME -> showPreview(PreviewScene.INITIAL)
            state.value.scene.endReason != null -> returnHome()
            // Active previews ask for confirmation in the UI before endPreview().
        }
    }

    private fun update(next: PhoneState) {
        savedStateHandle["name"] = next.name
        savedStateHandle["code"] = next.code
        savedStateHandle["scene"] = next.scene.name
        savedStateHandle["validationShown"] = next.validationShown
        mutableState.value = next
    }
}
