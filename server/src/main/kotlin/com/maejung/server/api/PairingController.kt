package com.maejung.server.api

import com.maejung.server.pairing.PairingCode
import com.maejung.server.pairing.PairingCodeStore
import com.maejung.server.session.Outbox
import com.maejung.server.token.Principal
import com.maejung.server.token.TokenService
import com.maejung.server.ws.Envelope
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Clock

data class PairingRequest(val code: String?, val riderName: String?)

data class PairingCompleted(val riderId: String, val vehicleId: String, val riderToken: String)

@RestController
class PairingController(
    private val tokenService: TokenService,
    private val codeStore: PairingCodeStore,
    private val outbox: Outbox,
    private val clock: Clock,
) {

    /** 차 화면의 "가족 연결" 버튼 */
    @PostMapping("/api/pairing-codes")
    @ResponseStatus(HttpStatus.CREATED)
    fun issueCode(@RequestHeader("Authorization") authorization: String): PairingCode {
        val principal = tokenService.verify(authorization.removePrefix("Bearer ").trim())
        if (principal !is Principal.Vehicle) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "차 토큰이 아닙니다")
        }
        return codeStore.issue(principal.vehicleId)
    }

    /** 폰에서 코드와 이름을 넣어 차와 연결한다 */
    @PostMapping("/api/pairings")
    @ResponseStatus(HttpStatus.CREATED)
    fun pair(@RequestBody request: PairingRequest): PairingCompleted {
        val riderName = request.riderName?.trim().orEmpty()
        if (riderName.length !in 1..20) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_RIDER_NAME", "이름은 1~20자여야 합니다")
        }
        val vehicleId = request.code?.let { codeStore.consume(it) }
            ?: throw ApiException(HttpStatus.NOT_FOUND, "PAIRING_CODE_NOT_FOUND", "코드가 없거나 만료됐습니다")
        val riderId = TokenService.newId("r")
        outbox.send(
            vehicleId,
            Envelope("pairing.completed", ts = clock.millis(), payload = mapOf("riderId" to riderId, "riderName" to riderName)),
        )
        return PairingCompleted(riderId, vehicleId, tokenService.issueRider(vehicleId, riderId))
    }
}
