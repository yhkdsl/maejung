package com.maejung.phone.ui

import androidx.lifecycle.SavedStateHandle
import com.maejung.phone.session.CodeError
import com.maejung.phone.session.NameError
import com.maejung.phone.session.PreviewScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneViewModelTest {
    @Test
    fun `blank names cannot advance even with a valid code`() {
        for (name in listOf("", "   ", "\t\n", "\u3000")) {
            val vm = model(name, "123456")
            vm.previewPairing()
            assertEquals(PreviewScene.INITIAL, vm.state.value.scene)
            assertEquals(NameError.REQUIRED, vm.state.value.nameError)
        }
    }

    @Test
    fun `names over twenty characters stay editable without being silently truncated`() {
        val name = "가".repeat(21)
        val vm = model(name, "123456")
        vm.previewPairing()
        assertEquals(PreviewScene.INITIAL, vm.state.value.scene)
        assertEquals(NameError.TOO_LONG, vm.state.value.nameError)
        assertEquals(name, vm.state.value.name)
    }

    @Test
    fun `twenty character names and a leading zero code can open only the home preview`() {
        val vm = model("가".repeat(20), "012345")
        assertTrue(vm.state.value.canPreviewPairing)
        vm.previewPairing()
        assertEquals(PreviewScene.HOME, vm.state.value.scene)
        assertEquals("012345", vm.state.value.code)
        assertTrue(vm.state.value.previewEnabled)
    }

    @Test
    fun `name limit matches server UTF16 length for supplementary unicode characters`() {
        val vm = model("😀".repeat(10), "012345")
        vm.previewPairing()
        assertEquals(PreviewScene.HOME, vm.state.value.scene)
        vm.showPreview(PreviewScene.INITIAL)
        vm.updateName("😀".repeat(11))
        vm.previewPairing()
        assertEquals(NameError.TOO_LONG, vm.state.value.nameError)
    }

    @Test
    fun `invalid codes including unicode digits are rejected without changing the entered code`() {
        for (code in listOf("", "12345", "1234567", "12a456", "１２３４５６", "١٢٣٤٥٦", "123 45", " 123456", "123456\n")) {
            val vm = model("민지", code)
            vm.previewPairing()
            assertEquals(PreviewScene.INITIAL, vm.state.value.scene, code)
            assertNotNull(vm.state.value.codeError, code)
            assertEquals(code, vm.state.value.code)
            assertFalse(vm.state.value.canPreviewPairing)
        }
    }

    @Test
    fun `fixing each input clears its error and permits a later preview`() {
        val vm = model("", "１２３４５６")
        vm.previewPairing()
        assertEquals(NameError.REQUIRED, vm.state.value.nameError)
        assertEquals(CodeError.SIX_ASCII_DIGITS, vm.state.value.codeError)
        vm.updateName("  민지  ")
        assertNull(vm.state.value.nameError)
        assertNotNull(vm.state.value.codeError)
        vm.updateCode("000001")
        assertNull(vm.state.value.codeError)
        assertTrue(vm.state.value.canPreviewPairing)
        vm.previewPairing()
        assertEquals(PreviewScene.HOME, vm.state.value.scene)
    }

    @Test
    fun `ordinary actions cannot skip the initial screen or waiting state`() {
        val vm = model("민지", "123456")
        vm.previewRequest()
        vm.endPreview()
        assertEquals(PreviewScene.INITIAL, vm.state.value.scene)
        vm.previewPairing()
        vm.previewRequest()
        assertEquals(PreviewScene.REQUESTING, vm.state.value.scene)
        vm.previewPairing()
        vm.previewRequest()
        assertEquals(PreviewScene.REQUESTING, vm.state.value.scene)
        assertNull(vm.state.value.sharing)
    }

    @Test
    fun `ending a pending or sharing preview allows a new request from home`() {
        for (scene in listOf(PreviewScene.REQUESTING, PreviewScene.SHARING, PreviewScene.NEARBY)) {
            val vm = model()
            vm.showPreview(scene)
            vm.endPreview()
            assertEquals(PreviewScene.ENDED_MANUAL, vm.state.value.scene)
            assertNull(vm.state.value.sharing)
            vm.returnHome()
            vm.previewRequest()
            assertEquals(PreviewScene.REQUESTING, vm.state.value.scene)
        }
    }

    @Test
    fun `selecting no data after nearby removes all sample telemetry`() {
        val vm = model()
        vm.showPreview(PreviewScene.NEARBY)
        val nearby = assertNotNull(vm.state.value.sharing)
        assertEquals(20, nearby.distanceMeters)
        assertTrue(nearby.lightOn == true)
        vm.showPreview(PreviewScene.SHARING_NO_DATA)
        val unknown = assertNotNull(vm.state.value.sharing)
        assertNull(unknown.distanceMeters)
        assertNull(unknown.bearingDegrees)
        assertNull(unknown.etaSeconds)
        assertNull(unknown.etaSource)
        assertNull(unknown.lightOn)
    }

    @Test
    fun `all ended previews can return home and retry without stale sharing data`() {
        for (scene in listOf(PreviewScene.ENDED_BOARDED, PreviewScene.ENDED_REJECTED,
            PreviewScene.ENDED_MANUAL, PreviewScene.ENDED_REQUEST_TIMEOUT,
            PreviewScene.ENDED_PEER_LOST, PreviewScene.ENDED_MAX_DURATION)) {
            val vm = model()
            vm.showPreview(PreviewScene.NEARBY)
            vm.showPreview(scene)
            assertEquals(scene, vm.state.value.scene)
            assertNull(vm.state.value.sharing)
            vm.returnHome()
            assertEquals(PreviewScene.HOME, vm.state.value.scene)
            vm.previewRequest()
            assertEquals(PreviewScene.REQUESTING, vm.state.value.scene)
        }
    }

    @Test
    fun `back from home and ended is predictable and preserves entered fields`() {
        val vm = model("민지", "001234")
        vm.previewPairing()
        vm.navigateBack()
        assertEquals(PreviewScene.INITIAL, vm.state.value.scene)
        assertEquals("민지", vm.state.value.name)
        assertEquals("001234", vm.state.value.code)
        vm.showPreview(PreviewScene.ENDED_BOARDED)
        vm.navigateBack()
        assertEquals(PreviewScene.HOME, vm.state.value.scene)
    }

    @Test
    fun `back does not silently end an active preview before UI confirmation`() {
        val vm = model()
        vm.showPreview(PreviewScene.NEARBY)
        vm.navigateBack()
        assertEquals(PreviewScene.NEARBY, vm.state.value.scene)
    }

    @Test
    fun `saved handle restores entered fields errors and chosen scene in a new view model`() {
        val handle = SavedStateHandle()
        val first = PhoneViewModel(handle, previewEnabled = true)
        first.updateName("민지")
        first.updateCode("12a")
        first.previewPairing()
        val restoredForm = PhoneViewModel(copyHandle(handle), previewEnabled = true)
        assertEquals("민지", restoredForm.state.value.name)
        assertEquals("12a", restoredForm.state.value.code)
        assertEquals(CodeError.SIX_ASCII_DIGITS, restoredForm.state.value.codeError)
        first.showPreview(PreviewScene.NEARBY)
        val restoredScene = PhoneViewModel(copyHandle(handle), previewEnabled = true)
        assertEquals(PreviewScene.NEARBY, restoredScene.state.value.scene)
        assertEquals(20, restoredScene.state.value.sharing?.distanceMeters)
    }

    @Test
    fun `unknown saved scene falls back to the editable initial screen`() {
        val handle = SavedStateHandle(mapOf("scene" to "future_scene", "name" to "민지"))
        val vm = PhoneViewModel(handle, previewEnabled = true)
        assertEquals(PreviewScene.INITIAL, vm.state.value.scene)
        assertEquals("민지", vm.state.value.name)
    }

    @Test
    fun `release ignores every preview command even with valid inputs`() {
        val vm = model("민지", "123456", previewEnabled = false)
        for (scene in PreviewScene.entries) {
            vm.showPreview(scene)
            vm.previewPairing()
            vm.previewRequest()
            vm.endPreview()
            vm.returnHome()
            vm.navigateBack()
            assertEquals(PreviewScene.INITIAL, vm.state.value.scene)
            assertFalse(vm.state.value.canPreviewPairing)
            assertFalse(vm.state.value.previewEnabled)
        }
    }

    @Test
    fun `release cannot restore a saved debug sharing scene`() {
        val handle = SavedStateHandle()
        val debug = PhoneViewModel(handle, previewEnabled = true)
        debug.updateName("민지")
        debug.showPreview(PreviewScene.NEARBY)
        val release = PhoneViewModel(copyHandle(handle), previewEnabled = false)
        assertEquals(PreviewScene.INITIAL, release.state.value.scene)
        assertNull(release.state.value.sharing)
        assertEquals("민지", release.state.value.name)
    }

    private fun model(name: String = "", code: String = "", previewEnabled: Boolean = true) =
        PhoneViewModel(SavedStateHandle(), previewEnabled).also {
            it.updateName(name)
            it.updateCode(code)
        }

    // Reconstruct from saved primitive values, rather than reusing the original handle/flow.
    private fun copyHandle(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
}
