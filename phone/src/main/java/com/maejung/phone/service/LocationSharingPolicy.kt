package com.maejung.phone.service

import com.maejung.phone.net.ConnectionState
import com.maejung.phone.session.PhonePhase

data class LocationPermissions(val fine: Boolean = false, val coarse: Boolean = false) {
    val granted: Boolean get() = fine || coarse
}

data class PickupLocationDecision(
    val requestPermission: Boolean = false,
    val sendPickup: Boolean = false,
    val notice: String? = null,
)

fun pickupLocationDecision(permissions: LocationPermissions): PickupLocationDecision = PickupLocationDecision(
    requestPermission = !permissions.granted,
    sendPickup = true,
    notice = if (permissions.granted) null else LOCATION_PERMISSION_NOTICE,
)

enum class LocationServiceAction { STOP, WAIT, START, KEEP }

data class LocationServiceDecision(val action: LocationServiceAction, val notice: String? = null)

fun locationServiceDecision(
    phase: PhonePhase,
    sessionId: String?,
    permissions: LocationPermissions,
    gpsEnabled: Boolean,
    activityResumed: Boolean,
    runningSessionId: String?,
    connection: ConnectionState = ConnectionState.CONNECTED,
): LocationServiceDecision = when {
    phase != PhonePhase.SHARING || sessionId.isNullOrBlank() -> LocationServiceDecision(LocationServiceAction.STOP)
    connection == ConnectionState.REPLACED || connection == ConnectionState.UNAUTHORIZED ->
        LocationServiceDecision(LocationServiceAction.STOP)
    !permissions.granted -> LocationServiceDecision(LocationServiceAction.STOP, LOCATION_PERMISSION_NOTICE)
    !gpsEnabled -> LocationServiceDecision(LocationServiceAction.STOP, LOCATION_GPS_NOTICE)
    runningSessionId == sessionId -> LocationServiceDecision(LocationServiceAction.KEEP)
    !activityResumed -> LocationServiceDecision(LocationServiceAction.WAIT, LOCATION_RESUME_NOTICE)
    else -> LocationServiceDecision(LocationServiceAction.START)
}

internal const val LOCATION_PERMISSION_NOTICE = "위치 권한이 없어 내 위치를 공유하지 못하고 있어요. 설정에서 위치 권한을 허용해 주세요"
internal const val LOCATION_GPS_NOTICE = "GPS가 꺼져 있어 내 위치를 공유하지 못하고 있어요. 위치 설정을 켜 주세요"
internal const val LOCATION_RESUME_NOTICE = "내 위치 공유를 시작하려면 앱을 다시 열어 주세요"
internal const val LOCATION_START_NOTICE = "위치 공유를 시작하지 못했어요. 앱을 다시 열어 주세요"
internal const val LOCATION_WAITING_NOTICE = "내 GPS 위치를 기다리고 있어요"
internal const val LOCATION_NOTIFICATION_NOTICE = "알림이 꺼져 있어요. 위치 공유는 앱의 종료 버튼으로 끝낼 수 있어요"
internal const val LOCATION_APPROXIMATE_NOTICE = "대략적 위치만 허용되어 GPS 위치를 받지 못할 수 있어요. 정확한 위치를 허용해 주세요"

/** Precision, fix availability, and notification visibility are independent notices. */
internal fun sharingLocationNotice(
    permissions: LocationPermissions,
    hasOwnLocation: Boolean,
    notificationsEnabled: Boolean,
    primaryNotice: String? = null,
): String? = buildList {
    primaryNotice?.let { add(it) }
    if (!permissions.granted) add(LOCATION_PERMISSION_NOTICE)
    else {
        if (!permissions.fine) add(LOCATION_APPROXIMATE_NOTICE)
        if (!hasOwnLocation && primaryNotice == null) add(LOCATION_WAITING_NOTICE)
    }
    if (!notificationsEnabled) add(LOCATION_NOTIFICATION_NOTICE)
}.distinct().takeIf { it.isNotEmpty() }?.joinToString("\n")
