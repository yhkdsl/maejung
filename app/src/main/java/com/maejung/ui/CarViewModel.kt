package com.maejung.ui

import androidx.lifecycle.ViewModel
import com.maejung.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    val connected: Boolean = false,
    val serverUrl: String = BuildConfig.SERVER_URL,
    val family: List<String> = emptyList(),
)

/** 화면 상태만 들고 있다. 세션은 CarSessionManager가 소유하고 여기서는 구독만 한다(A5) */
class CarViewModel : ViewModel() {
    private val _home = MutableStateFlow(HomeUiState())
    val home: StateFlow<HomeUiState> = _home.asStateFlow()
}
