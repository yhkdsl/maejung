package com.maejung.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maejung.phone.session.NameError
import com.maejung.phone.session.PhoneState

@Composable
fun PairScreen(
    state: PhoneState,
    onNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val submit = { focus.clearFocus(); onPreview() }
    PhonePage(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("엄마 차와\n연결해요", style = MaterialTheme.typography.headlineLarge)
                Text("이름과 차 화면의 코드를 입력해요.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ConnectionIllustration(Modifier.fillMaxWidth().height(50.dp))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("내 이름", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        placeholder = { Text("예: 민지") },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "내 이름" },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            errorContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        isError = state.nameError != null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                    )
                    Text(when (state.nameError) {
                        NameError.REQUIRED -> "이름을 입력해 주세요. 공백만 사용할 수 없어요."
                        NameError.TOO_LONG -> "이름은 20자까지 입력해 주세요."
                        null -> "${state.nameLength}/20자 · 이름 앞뒤 공백은 제외해요"
                    }, style = MaterialTheme.typography.bodySmall,
                        color = if (state.nameError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("차량 연결 코드", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    PairCodeField(state.code, onCodeChange, state.codeError != null,
                        onDone = { if (state.previewEnabled) submit() else focus.clearFocus() })
                    Text(if (state.codeError != null) "코드는 0–9 숫자 6자리로 입력해 주세요."
                        else "차 화면의 숫자 6자리 · 맨 앞의 0도 입력해요",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.codeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryAction(
                label = if (state.previewEnabled) "입력 확인 후 홈 미리보기" else "연결 서비스 준비 중",
                onClick = submit,
                enabled = state.previewEnabled,
            )
            Text(if (state.previewEnabled) "입력과 화면 이동만 확인해요. 실제 차와 연결되지 않아요."
                else "실제 연결 서비스 준비 중이에요. 연결하거나 요청을 보낼 수 없어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Six cells at rest; a visible standard editor while focused. Raw input is never filtered. */
@Composable
private fun PairCodeField(value: String, onValueChange: (String) -> Unit, isError: Boolean, onDone: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val useCells = !focused && value.length <= 6 && value.all { it in '0'..'9' }
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = "연결 코드"
                if (isError) error("코드는 0–9 숫자 6자리로 입력해 주세요.")
            },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (useCells) Color.Transparent else colors.onSurface),
        cursorBrush = SolidColor(if (useCells) Color.Transparent else colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { innerTextField ->
            if (useCells) {
                Box(contentAlignment = Alignment.CenterStart) {
                    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(6) { index ->
                            val border = if (isError) colors.error else colors.outline
                            Box(Modifier.weight(1f).heightIn(min = 56.dp)
                                .background(colors.surface, RoundedCornerShape(10.dp))
                                .border(1.dp, border, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text(value.getOrNull(index)?.toString().orEmpty(), fontSize = 23.sp,
                                    fontWeight = FontWeight.Medium, color = colors.primary)
                            }
                        }
                    }
                    Box(Modifier.matchParentSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) { innerTextField() }
                }
            } else {
                Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .background(colors.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, when {
                        isError -> colors.error
                        focused -> colors.primary
                        else -> colors.outline
                    }, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 15.dp)) { innerTextField() }
            }
        },
    )
}
