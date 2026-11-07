package com.maejung.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

private val PhoneColors = lightColorScheme(
    primary = Color(0xFF002C5F),
    onPrimary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF171F2A),
    surface = Color.White,
    onSurface = Color(0xFF171F2A),
    onSurfaceVariant = Color(0xFF5C6978),
    secondary = Color(0xFF00AAD2),
    secondaryContainer = Color(0xFFF6F2EA),
    onSecondaryContainer = Color(0xFF775A30),
    tertiary = Color(0xFFE3B461),
    outline = Color(0xFFBDC8D3),
    outlineVariant = Color(0xFFDCE2E8),
    error = Color(0xFFAA2435),
)

@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = PhoneColors,
        typography = base.copy(
            headlineLarge = base.headlineLarge.copy(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp),
            headlineSmall = base.headlineSmall.copy(fontSize = 26.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
            bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = (-0.2).sp),
            bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp),
            labelLarge = base.labelLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}

/** Keep the final action low when there is room; retain ordinary gaps when scrolling. */
private val PageArrangement = object : Arrangement.Vertical {
    override val spacing = 20.dp

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        var position = 0
        sizes.forEachIndexed { index, height ->
            outPositions[index] = if (index == sizes.lastIndex) maxOf(position, totalSize - height) else position
            position = outPositions[index] + height + spacing.roundToPx()
        }
    }
}

/** Every page can scroll with a keyboard, large font or short landscape viewport. */
@Composable
internal fun PhonePage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val gutter = if (maxWidth < 360.dp) 22.dp else 28.dp
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(horizontal = gutter, vertical = 18.dp),
            verticalArrangement = PageArrangement,
            content = content,
        )
    }
}

@Composable
internal fun PrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(15.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
internal fun SecondaryAction(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
internal fun PixelMark(modifier: Modifier = Modifier) {
    val navy = MaterialTheme.colorScheme.primary
    val cyan = MaterialTheme.colorScheme.secondary
    Canvas(modifier.size(18.dp)) {
        val unit = size.width / 20f
        fun square(x: Float, y: Float, side: Float, color: Color) =
            drawRect(color, Offset(x * unit, y * unit), Size(side * unit, side * unit))
        square(1f, 2f, 8f, navy)
        square(11f, 10f, 8f, navy)
        square(11f, 2f, 3f, cyan)
        square(6f, 15f, 3f, cyan)
    }
}
