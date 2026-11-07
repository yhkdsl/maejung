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
import com.maejung.phone.ui.PhoneApp
import com.maejung.phone.ui.PhoneTheme
import com.maejung.phone.ui.PhoneViewModel

class MainActivity : ComponentActivity() {
    private val model: PhoneViewModel by viewModels {
        viewModelFactory {
            initializer { PhoneViewModel(createSavedStateHandle(), previewEnabled = BuildConfig.DEBUG) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneTheme {
                val state by model.state.collectAsStateWithLifecycle()
                PhoneApp(
                    state = state,
                    onNameChange = model::updateName,
                    onCodeChange = model::updateCode,
                    onPairPreview = model::previewPairing,
                    onRequestPreview = model::previewRequest,
                    onEndPreview = model::endPreview,
                    onReturnHome = model::returnHome,
                    onBack = model::navigateBack,
                    onSelectPreview = model::showPreview,
                )
            }
        }
    }
}
