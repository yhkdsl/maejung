package com.maejung.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.maejung.BuildConfig
import com.maejung.session.CarSessionMachine
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

data class MapPoint(val lat: Double, val lng: Double)

/**
 * 공유 화면 지도(OpenStreetMap, osmdroid). 탑승자는 하늘색 동그라미, 차는 흰 테두리 남색 동그라미,
 * 차 주변 50m(실내등 켜지는 거리)는 옅은 원으로 그리고, 둘이 다 보이게 확대·이동한다.
 *
 * OSM 타일 정책을 지킨다: 앱 식별 User-Agent, 화면에 보이는 타일만 받기(미리 받기 없음), 출처 표시(CopyrightOverlay).
 * 공모전 시연·심사 규모용이다. 정식 출시 때는 상용 타일 업체나 카카오·네이버로 타일 주소만 바꾼다.
 */
@Composable
fun PickupMap(car: MapPoint?, rider: MapPoint?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val map = remember { createMap(context) }
    // 폰 앱 지도와 같은 색: 탑승자 하늘색(#00AAD2), 차 남색(#002C5F)
    val riderMarker = remember { dotMarker(map, Color.rgb(0, 170, 210), 44, "아이") }
    val carMarker = remember { dotMarker(map, Color.rgb(0, 44, 95), 52, "차") }
    val nearbyCircle = remember {
        Polygon(map).apply {
            fillPaint.color = Color.argb(40, 0, 170, 210)
            outlinePaint.color = Color.argb(140, 0, 170, 210)
            outlinePaint.strokeWidth = 3f
        }
    }

    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause() }
    }

    AndroidView(
        factory = { map },
        modifier = modifier,
        update = {
            map.overlays.removeAll(listOf(nearbyCircle, carMarker, riderMarker))
            car?.let {
                val point = GeoPoint(it.lat, it.lng)
                nearbyCircle.points = Polygon.pointsAsCircle(point, CarSessionMachine.NEARBY_ENTER_METERS)
                map.overlays.add(nearbyCircle)
                carMarker.position = point
                map.overlays.add(carMarker)
            }
            rider?.let {
                riderMarker.position = GeoPoint(it.lat, it.lng)
                map.overlays.add(riderMarker)
            }
            // 두 위치가 같으면 범위 크기가 0이라 세계 지도로 축소되므로 한 점으로 본다
            frame(map, listOfNotNull(car, rider).distinct())
            map.invalidate()
        },
    )
}

private fun createMap(context: Context): MapView {
    Configuration.getInstance().apply {
        // OSM 타일 정책: 앱을 식별할 수 있는 User-Agent가 있어야 한다
        userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
        osmdroidBasePath = context.filesDir
        osmdroidTileCache = context.cacheDir.resolve("osmdroid")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        controller.setZoom(17.0)
        overlays.add(CopyrightOverlay(context))
    }
}

private fun dotMarker(map: MapView, color: Int, sizePx: Int, title: String) = Marker(map).apply {
    icon = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(6, Color.WHITE)
        setSize(sizePx, sizePx)
    }
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    this.title = title
    setInfoWindow(null)
}

/** 점이 하나면 그 점을 가운데로, 둘이면 둘 다 보이게 */
private fun frame(map: MapView, points: List<MapPoint>) {
    when (points.size) {
        0 -> Unit
        1 -> {
            map.controller.setCenter(GeoPoint(points[0].lat, points[0].lng))
            map.controller.setZoom(18.0)
        }
        else -> map.post {
            val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.lat, it.lng) })
            // 두 점이 아주 가까우면 너무 확대되지 않게 최소 범위를 둔다
            map.zoomToBoundingBox(box.increaseByScale(1.6f), false, 80, 18.0, null)
        }
    }
}
