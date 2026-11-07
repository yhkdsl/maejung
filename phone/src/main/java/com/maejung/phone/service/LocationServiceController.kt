package com.maejung.phone.service

import com.maejung.phone.location.LocationFix

interface SharingLocationSource {
    fun start(onFix: (LocationFix) -> Unit, onUnavailable: (String) -> Unit)
    fun stop()
}

/** Session boundary shared by the Android service and local boundary tests. */
class LocationServiceController(
    private val source: SharingLocationSource,
    private val isCurrentSharingSession: (String) -> Boolean,
    private val onFix: (LocationFix) -> Unit,
    private val onClear: () -> Unit,
    private val onNotice: (String) -> Unit,
    private val onAppClosed: () -> Unit,
) {
    private var generation = 0L
    var sessionId: String? = null
        private set

    fun start(sessionId: String) {
        if (sessionId.isBlank() || !isCurrentSharingSession(sessionId)) return
        if (this.sessionId == sessionId) return
        stop()
        this.sessionId = sessionId
        val expectedGeneration = ++generation
        source.start(
            onFix = { fix ->
                if (generation == expectedGeneration && this.sessionId == sessionId &&
                    isCurrentSharingSession(sessionId)
                ) onFix(fix)
            },
            onUnavailable = { message ->
                if (generation == expectedGeneration && this.sessionId == sessionId &&
                    isCurrentSharingSession(sessionId)
                ) {
                    stop()
                    onNotice(message)
                }
            },
        )
    }

    fun stop() {
        generation++
        val hadSession = sessionId != null
        sessionId = null
        if (hadSession) {
            source.stop()
            onClear()
        }
    }

    fun taskRemoved() {
        stop()
        onAppClosed()
    }
}
