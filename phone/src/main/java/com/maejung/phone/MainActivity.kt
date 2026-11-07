package com.maejung.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maejung.phone.ui.LivePhoneApp
import com.maejung.phone.ui.PhoneTheme
import com.maejung.phone.ui.PhoneViewModel

class MainActivity : ComponentActivity() {
    private val previewModel: PhoneViewModel by viewModels {
        viewModelFactory {
            initializer { PhoneViewModel(createSavedStateHandle(), previewEnabled = BuildConfig.DEBUG) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = (application as MaejungPhoneApp).sessionManager
        setContent {
            PhoneTheme {
                val state by manager.state.collectAsStateWithLifecycle()
                LivePhoneApp(
                    state = state,
                    onPair = manager::pair,
                    onRequestPickup = manager::requestPickup,
                    onEndSession = manager::endSession,
                    onReconnect = manager::reconnect,
                    previewViewModel = previewModel,
                )
            }
        }
    }
}
