package com.maejung.server.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfig {
    // 만료 테스트에서 조작 가능한 시계로 바꿔 끼우기 위해 빈으로 둔다
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
