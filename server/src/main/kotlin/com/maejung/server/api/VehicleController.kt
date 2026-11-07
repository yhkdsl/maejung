package com.maejung.server.api

import com.maejung.server.token.TokenService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class VehicleRegistered(val vehicleId: String, val vehicleToken: String)

@RestController
class VehicleController(private val tokenService: TokenService) {

    /** 차 앱 첫 실행 때 한 번 부른다 */
    @PostMapping("/api/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(): VehicleRegistered {
        val vehicleId = TokenService.newId("v")
        return VehicleRegistered(vehicleId, tokenService.issueVehicle(vehicleId))
    }
}
