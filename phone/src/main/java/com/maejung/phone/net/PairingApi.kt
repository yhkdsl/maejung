package com.maejung.phone.net

import com.maejung.protocol.PairingResult

interface PairingApi {
    suspend fun pair(code: String, riderName: String): PairingResult
}
