package com.maejung.phone.store

data class StoredPairing(val riderId: String, val vehicleId: String, val riderToken: String, val riderName: String)

interface PairingStore {
    fun load(): StoredPairing?
    fun save(pairing: StoredPairing)
    fun clear()
}
