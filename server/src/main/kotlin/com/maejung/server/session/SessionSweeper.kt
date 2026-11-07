package com.maejung.server.session

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SessionSweeper(private val sessionService: SessionService) {
    @Scheduled(fixedDelay = 5_000)
    fun sweep() = sessionService.sweep()
}
