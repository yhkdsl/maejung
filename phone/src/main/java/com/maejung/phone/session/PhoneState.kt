package com.maejung.phone.session

import com.maejung.protocol.EndReasons
import com.maejung.protocol.EtaSources

/** Local presentation scenarios, never a network session or a claim of connectivity. */
enum class PreviewScene(val label: String, val endReason: String? = null) {
    INITIAL("초기 · 연결 입력"),
    HOME("홈 · 요청 전"),
    REQUESTING("요청 대기"),
    SHARING("공유 · 예시 120m"),
    SHARING_NO_DATA("공유 · 정보 없음"),
    NEARBY("근접 · 예시 20m"),
    ENDED_BOARDED("종료 · 탑승 확인", EndReasons.BOARDED),
    ENDED_REJECTED("종료 · 거절", EndReasons.REJECTED),
    ENDED_MANUAL("종료 · 직접 종료", EndReasons.MANUAL),
    ENDED_REQUEST_TIMEOUT("종료 · 요청 시간 초과", EndReasons.REQUEST_TIMEOUT),
    ENDED_PEER_LOST("종료 · 연결 끊김", EndReasons.PEER_LOST),
    ENDED_MAX_DURATION("종료 · 1시간 경과", EndReasons.MAX_DURATION);

    val isSharing: Boolean
        get() = this == SHARING || this == SHARING_NO_DATA || this == NEARBY
    val isActive: Boolean get() = this == REQUESTING || isSharing
}

enum class NameError { REQUIRED, TOO_LONG }
enum class CodeError { SIX_ASCII_DIGITS }

data class SharingInfo(
    val distanceMeters: Int? = null,
    val bearingDegrees: Int? = null,
    val etaSeconds: Int? = null,
    val etaSource: String? = null,
    val lightOn: Boolean? = null,
    val courseDegrees: Int? = null,
)

data class PhoneState(
    val name: String = "",
    val code: String = "",
    val scene: PreviewScene = PreviewScene.INITIAL,
    val previewEnabled: Boolean = false,
    val validationShown: Boolean = false,
) {
    // Match PairingController: trimmed Kotlin String.length, i.e. UTF-16 code units.
    val nameLength: Int get() = name.trim().length
    private val invalidName: NameError?
        get() = when {
            name.isBlank() -> NameError.REQUIRED
            nameLength > 20 -> NameError.TOO_LONG
            else -> null
        }
    private val invalidCode: Boolean get() = code.length != 6 || code.any { it !in '0'..'9' }
    val nameError: NameError?
        get() = if (validationShown || name.isNotEmpty()) invalidName else null
    val codeError: CodeError?
        get() = if ((validationShown || code.isNotEmpty()) && invalidCode) CodeError.SIX_ASCII_DIGITS else null
    val canPreviewPairing: Boolean get() = previewEnabled && invalidName == null && !invalidCode

    // Derived examples cannot leak into the missing-data scenario or an ended screen.
    val sharing: SharingInfo?
        get() = when (scene) {
            PreviewScene.SHARING -> SharingInfo(
                distanceMeters = 120, bearingDegrees = 80, etaSeconds = 90,
                etaSource = EtaSources.ESTIMATE, lightOn = false, courseDegrees = 35,
            )
            PreviewScene.NEARBY -> SharingInfo(
                distanceMeters = 20, bearingDegrees = 80, etaSeconds = 30,
                etaSource = EtaSources.ESTIMATE, lightOn = true, courseDegrees = 35,
            )
            PreviewScene.SHARING_NO_DATA -> SharingInfo()
            else -> null
        }
}
