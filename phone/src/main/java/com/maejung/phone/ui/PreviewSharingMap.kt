package com.maejung.phone.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val MapGround = Color(0xFFEEF1F3)
private val MapBuilding = Color(0xFFE2E7EB)
private val MapBorder = Color(0xFFD7DEE4)
private val MapLabel = Color(0xFF7C8C99)

/** The approved local drawing uses one north-up coordinate space, never GPS coordinates. */
@Composable
internal fun PreviewSharingMap(
    nearby: Boolean,
    showLocations: Boolean,
    courseDegrees: Int?,
    modifier: Modifier = Modifier,
    bottomOverlayHeightPx: Float = 0f,
) {
    val navy = MaterialTheme.colorScheme.primary
    val cyan = MaterialTheme.colorScheme.secondary
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val description = when {
        !showLocations -> "북쪽이 위인 예시 지도. 위치 정보가 없어 내 위치와 엄마 차 표시가 없어요."
        nearby -> "북쪽이 위인 확대 예시 지도. 내 위치가 정차한 엄마 차 가까이에 있어요."
        else -> "북쪽이 위인 예시 지도. 학원 앞 내 위치와 정차한 엄마 차 위치예요."
    }
    Canvas(modifier.semantics { contentDescription = description }) {
        drawRect(MapGround)
        // Retain the approved SVG crop wherever its complete marker labels fit.
        val viewWidth = if (nearby) 210f else 360f
        val viewHeight = if (nearby) 210f else 460f
        val centerX = if (nearby) 255f else 180f
        val centerY = if (nearby) 229f else 230f
        // Include label borders and the full course wedge in the critical world bounds.
        val left = if (nearby) 220.5f else 121.5f
        val right = if (nearby) 324.5f else 321.5f
        val top = if (nearby) 163.5f else 133.5f
        val bottom = if (nearby) 266.5f else 297.5f
        val edge = 8.dp.toPx()
        val contentBottom = (size.height - bottomOverlayHeightPx).coerceAtLeast(edge * 2f + 1f)
        val horizontalLimit = (size.width / 2f - edge).coerceAtLeast(1f) /
            maxOf(centerX - left, right - centerX)
        val verticalLimit = (contentBottom - edge * 2f) / (bottom - top)
        val mapScale = minOf(
            maxOf(size.width / viewWidth, size.height / viewHeight),
            horizontalLimit,
            verticalLimit,
        )
        // Move the unchanged geometry upward only when the measured HUD would cover it.
        val lowestCenter = edge + (centerY - top) * mapScale
        val highestCenter = maxOf(lowestCenter, contentBottom - edge - (bottom - centerY) * mapScale)
        val anchorY = (size.height / 2f).coerceIn(lowestCenter, highestCenter)
        val worldTop = centerY - anchorY / mapScale
        val worldBottom = centerY + (size.height - anchorY) / mapScale
        withTransform({
            translate(size.width / 2f, anchorY)
            scale(mapScale, mapScale, pivot = Offset.Zero)
            translate(-centerX, -centerY)
        }) {
            drawMapBlocks()
            drawMapRoads(worldTop, worldBottom)
            drawMapLabels(paint)
            if (showLocations) {
                drawChild(if (nearby) Offset(259f, 229f) else Offset(160f, 246f), nearby, courseDegrees, navy, cyan, paint)
                drawParkedCar(nearby, navy, paint)
            }
        }
    }
}

private fun DrawScope.drawMapBlocks() {
    val parcels = listOf(
        floatArrayOf(8f, 8f, 52f, 8f, 52f, 74f, 8f, 74f),
        floatArrayOf(77f, 8f, 130f, 8f, 134f, 74f, 77f, 74f),
        floatArrayOf(161f, 8f, 234f, 8f, 234f, 74f, 165f, 74f),
        floatArrayOf(259f, 8f, 350f, 8f, 350f, 74f, 259f, 74f),
        floatArrayOf(9f, 115f, 52f, 115f, 52f, 175f, 9f, 175f),
        floatArrayOf(77f, 115f, 133f, 115f, 137f, 182f, 77f, 182f),
        floatArrayOf(173f, 115f, 234f, 115f, 234f, 169f, 177f, 169f),
        floatArrayOf(259f, 115f, 349f, 115f, 349f, 158f, 259f, 158f),
        floatArrayOf(9f, 192f, 52f, 192f, 52f, 241f, 9f, 247f),
        floatArrayOf(77f, 198f, 138f, 198f, 141f, 226f, 77f, 237f),
        floatArrayOf(259f, 175f, 349f, 175f, 349f, 204f, 259f, 219f),
        floatArrayOf(9f, 288f, 52f, 281f, 52f, 355f, 9f, 355f),
        floatArrayOf(77f, 277f, 146f, 265f, 151f, 355f, 77f, 355f),
        floatArrayOf(179f, 261f, 234f, 252f, 234f, 355f, 185f, 355f),
        floatArrayOf(259f, 248f, 349f, 233f, 349f, 279f, 259f, 279f),
        floatArrayOf(259f, 296f, 349f, 296f, 349f, 355f, 259f, 355f),
        floatArrayOf(9f, 397f, 52f, 397f, 52f, 450f, 9f, 450f),
        floatArrayOf(77f, 397f, 153f, 397f, 156f, 450f, 77f, 450f),
        floatArrayOf(189f, 397f, 234f, 397f, 234f, 450f, 192f, 450f),
        floatArrayOf(259f, 397f, 350f, 397f, 350f, 450f, 259f, 450f),
    )
    parcels.forEach { points ->
        val path = polygon(*points)
        drawPath(path, MapBuilding)
        drawPath(path, MapBorder, style = Stroke(.7f))
    }
    val park = polygon(179f, 191f, 234f, 191f, 234f, 236f, 183f, 244f)
    drawPath(park, Color(0xFFE1EBE5))
    drawPath(park, Color(0xFFD1DED6), style = Stroke(1f))
    listOf(Offset(192f, 208f) to 5f, Offset(220f, 201f) to 5f,
        Offset(216f, 224f) to 6f, Offset(192f, 228f) to 4f).forEach { (point, radius) ->
        drawCircle(Color(0xFFD0DCCF), radius, point)
    }
    drawRoundRect(Color(0xFFD7E1E9), Offset(85f, 122f), Size(44f, 54f), CornerRadius(3f))
    drawRoundRect(Color(0xFFC4D1DC), Offset(85f, 122f), Size(44f, 54f), CornerRadius(3f), style = Stroke(1f))
    for (x in listOf(94f, 110f)) for (y in listOf(133f, 149f)) {
        drawRect(Color(0xFFB2C2D0), Offset(x, y), Size(7f, 7f))
    }
}

private fun DrawScope.drawMapRoads(worldTop: Float, worldBottom: Float) {
    val roadTop = minOf(-20f, worldTop)
    val roadBottom = maxOf(480f, worldBottom)
    val smallRoads = listOf(
        Offset(64f, roadTop) to Offset(64f, roadBottom),
        Offset(247f, roadTop) to Offset(247f, roadBottom),
        Offset(-10f, 94f) to Offset(370f, 94f),
        Offset(-10f, 379f) to Offset(370f, 379f),
    )
    smallRoads.forEach { (start, end) -> drawLine(Color(0xFFD6DFE5), start, end, 18f) }
    smallRoads.forEach { (start, end) -> drawLine(Color(0xFFFAFBFC), start, end, 15f) }
    val mainRoads = listOf(
        Offset(-20f, 273f) to Offset(380f, 207f),
        Offset(145f + (roadTop + 20f) * 44f / 500f, roadTop) to
            Offset(145f + (roadBottom + 20f) * 44f / 500f, roadBottom),
    )
    mainRoads.forEach { (start, end) -> drawLine(Color(0xFFD4DDE4), start, end, 31f) }
    mainRoads.forEach { (start, end) -> drawLine(Color.White, start, end, 28f) }
    mainRoads.forEach { (start, end) ->
        drawLine(Color(0xFFE7ECEF), start, end, 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
    for (i in 0..3) {
        val upper = Offset(152f + i * 5f, 225f - i)
        val lower = Offset(171f + i * 5f, 264f - i)
        rotate(-9f, upper) { drawRect(Color(0xFFCFD7DE), upper, Size(3f, 11f)) }
        rotate(-9f, lower) { drawRect(Color(0xFFCFD7DE), lower, Size(3f, 10f)) }
    }
}

private fun DrawScope.drawMapLabels(paint: Paint) {
    mapText("마중학원", 107f, 192f, 11f, Color(0xFF5D7182), paint, bold = true)
    mapText("편의점", 303f, 145f, 9f, MapLabel, paint)
    mapText("작은공원", 210f, 187f, 9f, MapLabel, paint)
    mapText("주택가", 110f, 324f, 9f, MapLabel, paint)
    mapText("카페", 304f, 326f, 9f, MapLabel, paint)
    mapText("아파트", 303f, 67f, 9f, MapLabel, paint)
    mapText("주택가", 207f, 431f, 9f, MapLabel, paint)
    rotate(-9f, Offset(314f, 249f)) { mapText("마중로", 314f, 249f, 9f, MapLabel, paint) }
    rotate(85f, Offset(202f, 333f)) { mapText("가족길", 202f, 333f, 9f, MapLabel, paint) }
}

private fun DrawScope.drawChild(
    location: Offset, nearby: Boolean, courseDegrees: Int?, navy: Color, cyan: Color, paint: Paint,
) {
    withTransform({ translate(location.x, location.y) }) {
        val markerScale = if (nearby) .64f else 1f
        drawCircle(cyan.copy(alpha = .12f), 25f * markerScale, Offset.Zero)
        if (courseDegrees != null) {
            withTransform({
                rotate(courseDegrees.toFloat(), pivot = Offset.Zero)
                scale(markerScale, markerScale, pivot = Offset.Zero)
            }) {
                val wedge = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(-13f, -29f)
                    quadraticTo(0f, -35f, 13f, -29f)
                    close()
                }
                drawPath(wedge, cyan.copy(alpha = .23f))
                drawPath(polygon(-6f, -23f, 0f, -31f, 6f, -23f), cyan, style = Stroke(1.8f))
            }
        }
        drawCircle(Color.White, 10f * markerScale, Offset.Zero)
        drawCircle(cyan, 6.5f * markerScale, Offset.Zero)
        if (nearby) {
            mapTag("내 위치", -38f, 17f, 53f, 20f, 11f, navy, paint)
        } else {
            mapTag("내 위치", -38f, 23f, 76f, 28f, 16f, navy, paint)
        }
    }
}

private fun DrawScope.drawParkedCar(nearby: Boolean, navy: Color, paint: Paint) {
    // This map-space position never changes when the child reaches the nearby example.
    withTransform({ translate(279f, 225f) }) {
        val markerScale = if (nearby) .64f else 1f
        withTransform({ scale(markerScale, markerScale, pivot = Offset.Zero) }) {
            drawOval(navy.copy(alpha = .13f), Offset(-12f, -3f), Size(24f, 8f))
            val pin = Path().apply {
                moveTo(0f, 0f)
                cubicTo(-5f, -9f, -22f, -18f, -22f, -33f)
                cubicTo(-22f, -45f, -12f, -55f, 0f, -55f)
                cubicTo(12f, -55f, 22f, -45f, 22f, -33f)
                cubicTo(22f, -18f, 5f, -9f, 0f, 0f)
                close()
            }
            drawPath(pin, navy)
            drawPath(pin, Color.White, style = Stroke(3f))
            withTransform({ translate(-13f, -47f); scale(26f / 24f, 26f / 24f, pivot = Offset.Zero) }) {
                drawPath(Path().apply {
                    moveTo(5f, 10f); lineTo(6.7f, 5f); lineTo(17.3f, 5f); lineTo(19f, 10f)
                    moveTo(5f, 10f); lineTo(19f, 10f); lineTo(20f, 11f); lineTo(20f, 17f)
                    lineTo(4f, 17f); lineTo(4f, 11f); close()
                    moveTo(6f, 17f); lineTo(6f, 19f); moveTo(18f, 17f); lineTo(18f, 19f)
                    moveTo(7f, 13f); lineTo(9f, 13f); moveTo(15f, 13f); lineTo(17f, 13f)
                }, Color.White, style = Stroke(1.7f))
            }
        }
        if (nearby) {
            mapTag("엄마 차", -12f, -61f, 57f, 20f, 11f, navy, paint)
        } else {
            mapTag("엄마 차", -42f, -91f, 84f, 29f, 16f, navy, paint)
        }
    }
}

private fun DrawScope.mapTag(
    text: String, left: Float, top: Float, width: Float, height: Float, fontSize: Float, color: Color, paint: Paint,
) {
    val corner = CornerRadius(if (fontSize < 12f) 7f else 10f)
    drawRoundRect(Color.White, Offset(left, top), Size(width, height), corner)
    drawRoundRect(Color(0xFFD7E1E7), Offset(left, top), Size(width, height), corner, style = Stroke(.8f))
    mapText(text, left + width / 2f, top + height * .7f, fontSize, color, paint, bold = true)
}

private fun DrawScope.mapText(
    text: String, x: Float, baseline: Float, fontSize: Float, color: Color, paint: Paint, bold: Boolean = false,
) {
    paint.color = color.toArgb()
    paint.textSize = fontSize
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    drawContext.canvas.nativeCanvas.drawText(text, x, baseline, paint)
}

private fun polygon(vararg points: Float) = Path().apply {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
    close()
}
