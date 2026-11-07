package com.maejung.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.graphics.PathParser

private fun vectorPath(data: String): Path = requireNotNull(PathParser.createPathFromPathData(data)).asComposePath()

private fun DrawScope.vectorViewport(width: Float, height: Float, draw: DrawScope.() -> Unit) {
    val factor = minOf(size.width / width, size.height / height)
    withTransform({
        translate((size.width - width * factor) / 2f, (size.height - height * factor) / 2f)
        scale(factor, factor, pivot = Offset.Zero)
    }, draw)
}

/** Original local connection motif; deliberately has no connected/live status. */
@Composable
internal fun ConnectionIllustration(modifier: Modifier = Modifier) {
    val phone = remember { vectorPath("M36 6h13q4 0 4 4v28q0 4-4 4H36q-4 0-4-4V10q0-4 4-4ZM39 36h7M39 11h7M73 24h27M196 24h27") }
    val car = remember { vectorPath("M242 15l6-9h20l6 9 5 4v15h-42V19ZM237 24h42M244 15h28M243 34v5M273 34v5") }
    Canvas(modifier) {
        vectorViewport(296f, 49f) {
            drawPath(phone, Color(0xFF8C9BAB), style = Stroke(1.4f))
            drawPath(car, Color(0xFF8C9BAB), style = Stroke(1.4f))
            repeat(4) { index ->
                drawRect(if (index == 3) Color(0xFF00AAD2) else Color(0xFF002C5F),
                    Offset(117f + 17f * index, 15f), Size(11f, 11f))
            }
            drawLine(Color(0xFF002C5F), Offset(242f, 27f), Offset(249f, 27f), 2.5f)
            drawLine(Color(0xFF002C5F), Offset(267f, 27f), Offset(274f, 27f), 2.5f)
        }
    }
}

/** Native rendition of the approved original silver EV SVG, with no brand or downloaded asset. */
@Composable
internal fun SilverVehicleIllustration(modifier: Modifier = Modifier) {
    val paths = remember {
        mapOf(
            "body" to vectorPath("M187 193l37-86q9-20 29-22 107-11 214 0 20 2 29 22l37 86 45 27q14 9 17 29l7 63q2 21-17 26H135q-19-5-17-26l7-63q3-20 17-29Z"),
            "glass" to vectorPath("M216 184l30-75q3-8 13-9 101-10 202 0 10 1 13 9l30 75Z"),
            "reflection" to vectorPath("M248 112l-23 62h72l44-70q-51 0-93 8Z"),
            "glassLines" to vectorPath("M260 102l54 79M460 102l-54 79"),
            "glassEdge" to vectorPath("M213 184q147-12 294 0"),
            "mirrors" to vectorPath("M184 191l-39-8q-9-2-11 5l-1 13q0 6 7 7l35 4M536 191l39-8q9-2 11 5l1 13q0 6-7 7l-35 4"),
            "hood" to vectorPath("M187 193l41-6q132-8 264 0l41 6 27 39q-200 17-400 0Z"),
            "hoodCreases" to vectorPath("M237 195l-26 38M483 195l26 38"),
            "hoodEdge" to vectorPath("M158 233q202 11 404 0"),
            "lamps" to vectorPath("M144 240l432-1 4 44q-220 13-440 0Z"),
            "lampEdge" to vectorPath("M145 241l431-1"),
            "bumper" to vectorPath("M141 288l49 14h340l49-14-8 35q-2 10-17 10H166q-15 0-17-10Z"),
            "intake" to vectorPath("M194 304l23 21h286l23-21Z"),
            "bottomEdge" to vectorPath("M168 337h384"),
            "sideEdges" to vectorPath("M133 291l12 35M587 291l-12 35"),
        )
    }
    Canvas(modifier.semantics { contentDescription = "은색 전기차 정면 예시 일러스트" }) {
        vectorViewport(720f, 420f) {
            drawOval(Color(0xFF173552).copy(alpha = .07f), Offset(108f, 343f), Size(504f, 28f))
            drawOval(Color(0xFF173552).copy(alpha = .07f), Offset(168f, 347f), Size(384f, 14f))
            listOf(127f, 545f).forEach { x ->
                drawRoundRect(Color(0xFF28343F), Offset(x, 268f), Size(48f, 84f), CornerRadius(14f))
            }
            drawPath(paths.getValue("body"), Brush.verticalGradient(
                0f to Color(0xFFFDFEFE), .34f to Color(0xFFE5EAF0), .7f to Color(0xFFC6CFD8), 1f to Color(0xFF9EABB9),
                startY = 80f, endY = 338f))
            drawPath(paths.getValue("body"), Color(0xFF95A4B3), style = Stroke(1.7f))
            drawPath(paths.getValue("glass"), Brush.linearGradient(
                listOf(Color(0xFF526372), Color(0xFF324351), Color(0xFF1D2E3D)), Offset(216f, 100f), Offset(504f, 184f)))
            drawPath(paths.getValue("glass"), Color(0xFF8F9EAC), style = Stroke(3f))
            drawPath(paths.getValue("reflection"), Color(0xFFA4B3C0).copy(alpha = .14f))
            drawPath(paths.getValue("glassLines"), Color(0xFFB6C2CD).copy(alpha = .16f), style = Stroke(1.1f))
            drawPath(paths.getValue("glassEdge"), Color(0xFF152735), style = Stroke(5f))
            drawPath(paths.getValue("mirrors"), Color(0xFF94A2B1))
            drawPath(paths.getValue("mirrors"), Color(0xFF8293A3), style = Stroke(1.5f))
            drawPath(paths.getValue("hood"), Brush.verticalGradient(listOf(Color(0xFFF9FBFC), Color(0xFFCBD4DD)), 184f, 241f))
            drawPath(paths.getValue("hoodCreases"), Color(0xFFB2BFCC), style = Stroke(1.5f))
            drawPath(paths.getValue("hoodEdge"), Color.White.copy(alpha = .85f), style = Stroke(3f))
            drawPath(paths.getValue("lamps"), Color(0xFF233746))
            drawPath(paths.getValue("lampEdge"), Color(0xFF788B9D), style = Stroke(2f))
            listOf(157f, 487f).forEach { start ->
                repeat(4) { index ->
                    val x = start + index * 20f
                    listOf(250f to 13f, 267f to 8f).forEach { (y, height) ->
                        drawRoundRect(Color(0xFFEAF8FD), Offset(x, y), Size(16f, height), CornerRadius(1f))
                        drawRoundRect(Color(0xFFA1BDCD), Offset(x, y), Size(16f, height), CornerRadius(1f), style = Stroke(1f))
                    }
                }
            }
            drawPath(paths.getValue("bumper"), Brush.verticalGradient(listOf(Color(0xFF8796A6), Color(0xFFBCC6D0)), 288f, 333f))
            drawPath(paths.getValue("intake"), Color(0xFF354858))
            drawLine(Color(0xFF687C8E), Offset(231f, 311f), Offset(489f, 311f), 2f)
            drawRoundRect(Color(0xFFEAF0F4), Offset(322f, 294f), Size(76f, 25f), CornerRadius(2f))
            drawRoundRect(Color(0xFFA6B4C1), Offset(322f, 294f), Size(76f, 25f), CornerRadius(2f), style = Stroke(1f))
            listOf(Offset(341f, 303f), Offset(359f, 307f), Offset(370f, 303f)).forEach { offset ->
                drawRect(Color(0xFF8497A8), offset, Size(8f, 8f))
            }
            drawRect(Color(0xFF8497A8), Offset(352f, 303f), Size(4f, 4f))
            drawPath(paths.getValue("bottomEdge"), Color(0xFF768999), style = Stroke(2f))
            drawPath(paths.getValue("sideEdges"), Color(0xFFE6EDF3), style = Stroke(2f))
        }
    }
}
