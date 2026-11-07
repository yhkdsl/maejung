package com.maejung.phone

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maejung.phone.ui.LivePhoneApp
import com.maejung.phone.ui.PhoneTheme
import com.maejung.phone.ui.PhoneViewModel
import com.maejung.phone.service.locationPermissions
import com.maejung.phone.service.pickupLocationDecision

class MainActivity : ComponentActivity() {
    private val phoneApp: MaejungPhoneApp get() = application as MaejungPhoneApp
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        phoneApp.reconcileLocationSharing()
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        phoneApp.reconcileLocationSharing()
        if (locationPermissions(this).granted) requestNotificationPermission()
    }
    private val previewModel: PhoneViewModel by viewModels {
        viewModelFactory {
            initializer { PhoneViewModel(createSavedStateHandle(), previewEnabled = BuildConfig.DEBUG) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = phoneApp.sessionManager
        setContent {
            PhoneTheme {
                val state by manager.state.collectAsStateWithLifecycle()
                LivePhoneApp(
                    state = state,
                    onPair = manager::pair,
                    onRequestPickup = ::requestPickup,
                    onEndSession = manager::endSession,
                    onReconnect = manager::reconnect,
                    previewViewModel = previewModel,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        phoneApp.setActivityResumed(true)
    }

    override fun onPause() {
        phoneApp.setActivityResumed(false)
        super.onPause()
    }

    private fun requestPickup() {
        val manager = phoneApp.sessionManager
        if (!manager.state.value.canRequest) return
        val decision = pickupLocationDecision(locationPermissions(this))
        // Location denial never suppresses the pickup request.
        if (decision.sendPickup) manager.requestPickup()
        manager.setLocationNotice(decision.notice)
        if (decision.requestPermission) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
