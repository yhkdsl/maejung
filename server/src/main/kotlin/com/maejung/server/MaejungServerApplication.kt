package com.maejung.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class MaejungServerApplication

fun main(args: Array<String>) {
    runApplication<MaejungServerApplication>(*args)
}
