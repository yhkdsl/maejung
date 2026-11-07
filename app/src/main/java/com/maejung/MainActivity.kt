package com.maejung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maejung.ui.CarViewModel
import com.maejung.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: CarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 주행 중 화면이라 다크 배경을 기본으로 한다
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    val home by viewModel.home.collectAsStateWithLifecycle()
                    HomeScreen(state = home, onPairClick = {})
                }
            }
        }
    }
}
