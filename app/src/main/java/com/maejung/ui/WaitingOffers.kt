package com.maejung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maejung.session.Phase
import com.maejung.vehicle.Gear

/** 대기 중 주변 혜택 한 줄. 가게 이름은 지어낸 것이다(실제 브랜드를 쓰지 않는다) */
data class WaitingOffer(val place: String, val benefit: String, val distanceM: Int)

/**
 * 수익 모델 "대기 중 주변 혜택"의 데모 데이터(기획서 사업 가설). 광고 SDK·서버 없이 앱에 고정으로 둔다.
 * 실제 제휴가 아니므로 화면에 "제휴 예시"를 함께 표시한다
 */
val DEMO_OFFERS = listOf(
    WaitingOffer("대치 커피 드라이브스루", "아메리카노 1,000원 할인", 120),
    WaitingOffer("학원가 편의점", "음료 1+1", 80),
)

/**
 * 기어 P로 기다리는 Sharing에서만 보여준다. 주행 중(P 아님)에는 바로 숨기고,
 * Nearby부터는 아이를 찾는 데 집중하도록 숨긴다
 */
fun waitingOffers(phase: Phase, gear: Gear?): List<WaitingOffer> =
    if (phase is Phase.Sharing && gear == Gear.PARK) DEMO_OFFERS else emptyList()

/** 누르는 곳 없는 안내 패널. 공유 화면의 주요 버튼 수(최대 2개)를 늘리지 않는다 */
@Composable
fun WaitingOffersPanel(offers: List<WaitingOffer>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, CarShapes.Panel)
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("기다리는 동안 근처 혜택", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("제휴 예시", fontSize = 28.sp, color = MaterialTheme.colorScheme.tertiary)
        }
        offers.forEach {
            Column {
                Text(it.benefit, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("${it.place} · ${it.distanceM}m", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
