package com.maejung.server.pairing

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration

data class PairingCode(val code: String, val expiresAt: Long)

/** 차 화면에 띄우는 6자리 연결 코드. 메모리에만 두고, 10분 지나거나 한 번 쓰면 사라진다 */
@Component
class PairingCodeStore(private val clock: Clock) {
    private data class Entry(val vehicleId: String, val expiresAt: Long)

    private val random = SecureRandom()
    private val byCode = HashMap<String, Entry>()
    private val codeByVehicle = HashMap<String, String>()

    @Synchronized
    fun issue(vehicleId: String): PairingCode {
        val now = clock.millis()
        purgeExpired(now)
        // 같은 차가 새 코드를 받으면 이전 코드는 무효가 된다
        codeByVehicle.remove(vehicleId)?.let { byCode.remove(it) }
        var code: String
        do {
            code = "%06d".format(random.nextInt(1_000_000))
        } while (byCode.containsKey(code))
        val expiresAt = now + TTL.toMillis()
        byCode[code] = Entry(vehicleId, expiresAt)
        codeByVehicle[vehicleId] = code
        return PairingCode(code, expiresAt)
    }

    /** 유효한 코드면 차 id를 돌려주고 코드를 지운다 */
    @Synchronized
    fun consume(code: String): String? {
        purgeExpired(clock.millis())
        val entry = byCode.remove(code) ?: return null
        codeByVehicle.remove(entry.vehicleId)
        return entry.vehicleId
    }

    private fun purgeExpired(now: Long) {
        val expired = byCode.filterValues { it.expiresAt <= now }
        expired.forEach { (code, entry) ->
            byCode.remove(code)
            codeByVehicle.remove(entry.vehicleId, code)
        }
    }

    companion object {
        val TTL: Duration = Duration.ofMinutes(10)
    }
}
