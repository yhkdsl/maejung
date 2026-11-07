package com.maejung.phone.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.maejung.phone.BuildConfig
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

/** Actual north-up OSM tiles; the P2 illustration is isolated in PreviewSharingMap. */
@Composable
internal fun SharingMap(locations: SharingMapLocations, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val liveMap = remember(context) { LiveSharingMap(context) }
    DisposableEffect(liveMap, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> liveMap.map.onResume()
                Lifecycle.Event.ON_PAUSE -> liveMap.map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) liveMap.map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            liveMap.map.onPause()
            liveMap.map.onDetach()
        }
    }
    AndroidView(
        factory = { liveMap.map },
        modifier = modifier.clipToBounds().semantics {
            contentDescription = when {
                locations.car != null && locations.rider != null ->
                    "OpenStreetMap 지도. 남색은 엄마 차, 하늘색은 내 위치. 지도 위쪽은 북쪽이에요."
                locations.car != null -> "OpenStreetMap 지도. 남색은 엄마 차. 내 위치를 기다리고 있어요."
                locations.rider != null -> "OpenStreetMap 지도. 하늘색은 내 위치. 차 위치를 기다리고 있어요."
                else -> "OpenStreetMap 지도. 위치 정보를 기다리고 있어요."
            }
        },
        update = { liveMap.update(locations) },
    )
}

private class LiveSharingMap(context: Context) {
    private val density = context.resources.displayMetrics.density
    val map: MapView
    private val carMarker: Marker
    private val riderMarker: Marker
    private var locations: SharingMapLocations? = null

    init {
        Configuration.getInstance().apply {
            userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = context.filesDir
            osmdroidTileCache = context.cacheDir.resolve("osmdroid")
        }
        map = MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            // The HUD lives below the map, leaving this attribution unobscured.
            overlays.add(CopyrightOverlay(context).apply {
                setTextColor(Color.rgb(0, 44, 95))
                // CopyrightOverlay applies display density internally.
                setTextSize(11)
                setOffset((8 * density).toInt(), (8 * density).toInt())
                setAlignBottom(true)
                setAlignRight(false)
            })
            setOnTouchListener { view, event ->
                view.parent?.requestDisallowInterceptTouchEvent(
                    event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL,
                )
                false
            }
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) frame()
            }
        }
        carMarker = dotMarker(Color.rgb(0, 44, 95), 26, "엄마 차")
        riderMarker = dotMarker(Color.rgb(0, 170, 210), 22, "내 위치")
    }

    fun update(next: SharingMapLocations) {
        if (locations == next) return
        locations = next
        map.overlays.remove(carMarker)
        map.overlays.remove(riderMarker)
        next.car?.let {
            carMarker.position = GeoPoint(it.lat, it.lng)
            map.overlays.add(carMarker)
        }
        next.rider?.let {
            riderMarker.position = GeoPoint(it.lat, it.lng)
            map.overlays.add(riderMarker)
        }
        frame()
        map.invalidate()
    }

    private fun dotMarker(color: Int, sizeDp: Int, label: String) = Marker(map).apply {
        icon = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke((3 * density).toInt(), Color.WHITE)
            setSize((sizeDp * density).toInt(), (sizeDp * density).toInt())
        }
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        title = label
        setInfoWindow(null)
    }

    private fun frame() {
        if (map.width == 0 || map.height == 0) return
        val points = locations?.let { listOfNotNull(it.car, it.rider).distinct() }.orEmpty()
        when (points.size) {
            0 -> Unit
            1 -> {
                map.controller.setCenter(GeoPoint(points[0].lat, points[0].lng))
                map.controller.setZoom(18.0)
            }
            else -> {
                val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.lat, it.lng) })
                map.zoomToBoundingBox(box.increaseByScale(1.6f), false, (32 * density).toInt(), 18.0, null)
            }
        }
    }
}
