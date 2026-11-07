package com.maejung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maejung.eta.Eta
import com.maejung.protocol.EndReasons
import com.maejung.protocol.EtaSources
import com.maejung.session.toSubject
import com.maejung.session.toWith

// 주행 중 화면 원칙(docs/CAR_APP.md 4절): 버튼은 화면 높이 1/4 이상, 글자 32sp 이상, 화면당 버튼 최대 2개

/** 마중 앱이 앞에 있을 때의 요청 화면. 다른 앱이 앞에 있으면 헤드업 알림이 대신한다(A8) */
@Composable
fun RequestScreen(riderName: String, onAccept: () -> Unit, onReject: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Text("${riderName.toSubject()} 데리러 와 달래요", fontSize = 48.sp)
        Text("수락하면 위치를 공유하고, 멀리 있으면 길안내를 시작해요", fontSize = 28.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f).height(180.dp)) {
                Text("거절", fontSize = 40.sp)
            }
            Button(onClick = onAccept, modifier = Modifier.weight(1f).height(180.dp)) {
                Text("수락", fontSize = 40.sp)
            }
        }
    }
}

@Composable
fun SharingScreen(
    riderName: String,
    distanceM: Double?,
    bearing: Float?,
    eta: Eta?,
    car: MapPoint?,
    rider: MapPoint?,
    nearby: Boolean,
    lightOn: Boolean,
    onEnd: () -> Unit,
) {
    // 왼쪽은 지도(아이·차 위치), 오른쪽은 큰 글씨 안내와 버튼 하나
    Row(modifier = Modifier.fillMaxSize()) {
        // MapView는 자기 영역 밖까지 그려서 오른쪽 글자를 덮는다. 영역 안으로 자르고 오른쪽은 배경을 칠한다
        PickupMap(car = car, rider = rider, modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds())
        SharingInfo(
            riderName, distanceM, bearing, eta, nearby, lightOn, onEnd,
            Modifier.weight(1f).background(MaterialTheme.colorScheme.surface),
        )
    }
}

@Composable
private fun SharingInfo(
    riderName: String,
    distanceM: Double?,
    bearing: Float?,
    eta: Eta?,
    nearby: Boolean,
    lightOn: Boolean,
    onEnd: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight().padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            if (nearby) "${riderName.toSubject()} 가까이 왔어요" else "${riderName.toWith()} 위치 공유 중",
            fontSize = 48.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Text(distanceM?.let { "${it.toInt()}m" } ?: "거리 확인 중", fontSize = 96.sp)
            // 정차 중에는 차 방향을 알 수 없어 화살표 대신 방위(북동쪽 등)로 보여준다
            // 아주 가까우면 방위가 의미 없어 숨긴다
            if (bearing != null && (distanceM ?: 0.0) >= 5.0) {
                Text(compassName(bearing), fontSize = 48.sp, modifier = Modifier.padding(top = 32.dp))
            }
        }
        eta?.let {
            val label = if (it.source == EtaSources.NAVI) "길안내 기준" else "예상"
            val text = if (it.seconds < 30) "곧 도착 ($label)" else "도착까지 약 ${(it.seconds + 59) / 60}분 ($label)"
            Text(text, fontSize = 36.sp)
        }
        if (lightOn) Text("실내등을 켰어요", fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
        Button(
            onClick = onEnd,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Text("공유 끝내기", fontSize = 36.sp, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/** 북쪽 기준 방위각 → 8방위 이름 */
private fun compassName(bearing: Float): String {
    val names = listOf("북쪽", "북동쪽", "동쪽", "남동쪽", "남쪽", "남서쪽", "서쪽", "북서쪽")
    val normalized = (bearing % 360 + 360) % 360
    return names[((normalized + 22.5f) / 45f).toInt() % 8]
}

@Composable
fun EndedScreen(reason: String) {
    val text = when (reason) {
        EndReasons.BOARDED -> "탑승 확인! 위치공유를 끝냈어요"
        EndReasons.REJECTED -> "요청을 거절했어요"
        EndReasons.MANUAL -> "위치공유를 끝냈어요"
        EndReasons.REQUEST_TIMEOUT -> "요청이 시간 초과로 끝났어요"
        EndReasons.PEER_LOST -> "연결이 끊겨서 위치공유를 끝냈어요"
        EndReasons.MAX_DURATION -> "1시간이 지나 위치공유를 끝냈어요"
        else -> "위치공유를 끝냈어요"
    }
    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Text(text, fontSize = 48.sp)
    }
}
