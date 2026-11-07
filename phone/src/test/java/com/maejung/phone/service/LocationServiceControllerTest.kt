package com.maejung.phone.service

import com.maejung.phone.location.LocationFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocationServiceControllerTest {
    @Test fun `same sharing session subscribes only once and forwards fixes`() {
        val fixture = Fixture()
        fixture.controller.start("s1")
        fixture.controller.start("s1")
        fixture.source.callbacks.single()(fixture.fix)
        assertEquals(1, fixture.source.starts)
        assertEquals(listOf(fixture.fix), fixture.fixes)
    }

    @Test fun `callbacks from replaced session cannot update the current session`() {
        val fixture = Fixture()
        fixture.controller.start("s1")
        val oldCallback = fixture.source.callbacks.single()
        fixture.current = "s2"
        fixture.controller.start("s2")
        oldCallback(fixture.fix)
        fixture.source.callbacks.last()(fixture.fix.copy(lat = 38.0))
        assertEquals(listOf(fixture.fix.copy(lat = 38.0)), fixture.fixes)
    }

    @Test fun `stop clears own fix and invalidates an already queued callback`() {
        val fixture = Fixture()
        fixture.controller.start("s1")
        val callback = fixture.source.callbacks.single()
        fixture.controller.stop()
        callback(fixture.fix)
        assertEquals(1, fixture.source.stops)
        assertEquals(1, fixture.clears)
        assertEquals(emptyList(), fixture.fixes)
        assertNull(fixture.controller.sessionId)
    }

    @Test fun `callback is discarded when manager is no longer sharing`() {
        val fixture = Fixture()
        fixture.controller.start("s1")
        fixture.current = null
        fixture.source.callbacks.single()(fixture.fix)
        assertEquals(emptyList(), fixture.fixes)
    }

    @Test fun `task removal stops GPS and reports app closed`() {
        val fixture = Fixture()
        fixture.controller.start("s1")
        fixture.controller.taskRemoved()
        assertEquals(1, fixture.appClosed)
        assertEquals(1, fixture.source.stops)
        assertEquals(1, fixture.clears)
        assertNull(fixture.controller.sessionId)
    }

    private class Fixture {
        val source = Source()
        val fix = LocationFix(37.5, 127.0, 5f, timestampMillis = 100_000)
        val fixes = mutableListOf<LocationFix>()
        var current: String? = "s1"
        var clears = 0
        var appClosed = 0
        val controller = LocationServiceController(source, { it == current }, fixes::add,
            { clears++ }, {}, { appClosed++ })
    }

    private class Source : SharingLocationSource {
        val callbacks = mutableListOf<(LocationFix) -> Unit>()
        var starts = 0
        var stops = 0
        override fun start(onFix: (LocationFix) -> Unit, onUnavailable: (String) -> Unit) {
            starts++
            callbacks += onFix
        }
        override fun stop() { stops++ }
    }
}
