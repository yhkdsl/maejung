package com.maejung.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 연결 상태, 연결된 가족, 가족 연결 버튼. 운전 중에는 이 화면을 볼 일이 거의 없다(요청은 헤드업 알림으로 온다) */
@Composable
fun HomeScreen(state: HomeUiState, onPairClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("마중", fontSize = 56.sp, style = MaterialTheme.typography.headlineLarge)
        Text(
            if (state.connected) "서버 연결됨" else "서버 연결 전",
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (state.family.isEmpty()) "연결된 가족이 없어요" else "연결된 가족: ${state.family.joinToString()}",
            fontSize = 32.sp,
        )
        Button(
            onClick = onPairClick,
            enabled = state.connected,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        ) {
            Text("가족 연결", fontSize = 36.sp)
        }
    }
}
