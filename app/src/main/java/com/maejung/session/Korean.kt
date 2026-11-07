package com.maejung.session

/** 이름 끝 글자에 받침이 있는지. 한글이 아니면 받침 없음으로 본다 */
private fun String.endsWithBatchim(): Boolean {
    val last = trimEnd().lastOrNull() ?: return false
    if (last !in '가'..'힣') return false
    return (last - '가') % 28 != 0
}

/** 민지 → 민지가, 준영 → 준영이가 */
fun String.toSubject(): String = if (endsWithBatchim()) "${this}이가" else "${this}가"

/** 민지 → 민지에게, 준영 → 준영이에게 */
fun String.toObject(): String = if (endsWithBatchim()) "${this}이에게" else "${this}에게"

enum class VoiceAnswer { ACCEPT, REJECT }

/**
 * 음성 인식 결과를 승인·거절로 바꾼다. 거절 단어를 먼저 본다("안 갈게"에 "갈게"가 들어 있어서).
 * 둘 다 없으면 null이고, 그 결과는 무시한다.
 */
object VoiceAnswers {
    // "갈"은 "가"와 다른 글자라 "안 갈게"를 따로 넣는다
    private val reject = listOf("아니", "안 돼", "안돼", "싫어", "안 가", "안가", "안 갈", "안갈", "못 가", "못가", "못 갈", "못갈", "거절")
    // "어"는 "어디야" 같은 말에도 들어 있어 넣지 않는다
    private val accept = listOf("응", "그래", "갈게", "좋아", "네", "예", "수락", "알았어", "오케이")

    fun parse(text: String?): VoiceAnswer? {
        val normalized = text?.trim() ?: return null
        return when {
            reject.any { it in normalized } -> VoiceAnswer.REJECT
            accept.any { it in normalized } -> VoiceAnswer.ACCEPT
            else -> null
        }
    }
}
