package com.maejung.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 폰 앱과 같은 브랜드 색(남색 #002C5F, 진주색 #F7F8FA, 하늘색 #00AAD2, 호박색 #E3B461).
// 차는 밤에도 보는 주행 화면이라 남색을 배경으로 깔고, 남색 위에서 보이도록 하늘색을 주 색으로 쓴다
object CarColors {
    val Navy = Color(0xFF002C5F)
    val NavyDeep = Color(0xFF0A1B30)
    val NavyPanel = Color(0xFF12294A)
    val Pearl = Color(0xFFF7F8FA)
    val Cyan = Color(0xFF00AAD2)
    val Amber = Color(0xFFE3B461)
    val Muted = Color(0xFFA9B8C9)
    val Line = Color(0xFF2C4467)
}

private val Scheme = darkColorScheme(
    primary = CarColors.Cyan,
    onPrimary = CarColors.NavyDeep,
    background = CarColors.NavyDeep,
    onBackground = CarColors.Pearl,
    surface = CarColors.NavyDeep,
    onSurface = CarColors.Pearl,
    surfaceVariant = CarColors.NavyPanel,
    onSurfaceVariant = CarColors.Muted,
    secondary = CarColors.Cyan,
    tertiary = CarColors.Amber,
    outline = CarColors.Line,
    outlineVariant = CarColors.Line,
    error = Color(0xFFFF8A94),
)

object CarShapes {
    val Button = RoundedCornerShape(28.dp)
    val Panel = RoundedCornerShape(24.dp)
}

@Composable
fun CarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

/** 폰 앱 상단과 같은 마중 픽셀 로고 + 글자 */
@Composable
fun MaejungLogo(markSize: Dp = 44.dp, fontSize: TextUnit = 48.sp) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelMark(Modifier.size(markSize))
        Text("마중", fontSize = fontSize, fontWeight = FontWeight.Bold, color = CarColors.Pearl)
    }
}

/** 폰 앱 PixelMark와 같은 모양. 남색 배경에서 보이도록 남색 칸을 진주색으로 바꿨다 */
@Composable
fun PixelMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.width / 20f
        fun square(x: Float, y: Float, side: Float, color: Color) =
            drawRect(color, Offset(x * unit, y * unit), Size(side * unit, side * unit))
        square(1f, 2f, 8f, CarColors.Pearl)
        square(11f, 10f, 8f, CarColors.Pearl)
        square(11f, 2f, 3f, CarColors.Cyan)
        square(6f, 15f, 3f, CarColors.Cyan)
    }
}
