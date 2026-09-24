package com.dastyar.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ui.theme.Purple

/**
 * A centred question card: an emoji badge, the question, an optional hint and
 * the answer area. Used across the questionnaire so every step looks the same.
 */
@Composable
fun QuestionCard(
    emoji: String,
    question: String,
    hint: String? = null,
    accent: Color = Purple,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(accent.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 23.sp) }

            Spacer(Modifier.height(12.dp))
            Text(
                question,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (hint != null) {
                Spacer(Modifier.height(5.dp))
                Text(
                    hint,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/**
 * Selectable chip. Works for both single and multi choice questions.
 * Selected chips fill with the accent colour and lift slightly.
 * [compact] shrinks it for dense grids such as the month picker.
 */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    accent: Color = Purple,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (selected) accent else MaterialTheme.colorScheme.surfaceVariant,
        label = "chipBg"
    )
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, label = "chipScale")

    Surface(
        shape = RoundedCornerShape(if (compact) 12.dp else 15.dp),
        color = bg,
        modifier = modifier
            .scale(scale)
            .clickable { onClick() }
    ) {
        Row(
            Modifier.padding(
                horizontal = if (compact) 6.dp else 15.dp,
                vertical = if (compact) 9.dp else 11.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (selected && !compact) {
                Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                fontSize = if (compact) 11.5.sp else 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                // Persian words must never break apart letter by letter
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** Multi-select version of ChoiceChips, backed by a comma-joined string list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiChoiceChips(
    options: List<String>,
    selected: List<String>,
    accent: Color = Purple,
    onChange: (List<String>) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        options.forEach { option ->
            val isOn = option in selected
            SelectChip(label = option, selected = isOn, accent = accent) {
                onChange(
                    if (isOn) selected - option else selected + option
                )
            }
        }
    }
}

/**
 * Single-choice chips. Tapping the selected chip again keeps it selected, so a
 * value can never be accidentally cleared.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleChoiceChips(
    options: List<String>,
    selected: String,
    accent: Color = Purple,
    compact: Boolean = false,
    onChange: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        options.forEach { option ->
            SelectChip(
                label = option,
                selected = option == selected,
                accent = accent,
                compact = compact
            ) { onChange(option) }
        }
    }
}

/** Turns a stored comma-joined string into a list. */
fun splitMulti(value: String): List<String> =
    if (value.isBlank()) emptyList() else value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** Turns a list back into a stored comma-joined string. */
fun joinMulti(values: List<String>): String = values.joinToString(",")

/** A small gradient progress pill shown at the top of each questionnaire step. */
@Composable
fun StepBadge(step: Int, total: Int, label: String) {
    val brush = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "${com.dastyar.app.data.Dates.fa(step + 1)} / ${com.dastyar.app.data.Dates.fa(total)}",
                    color = Color.White.copy(alpha = .9f), fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(total) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (i <= step) Color.White
                                else Color.White.copy(alpha = .3f)
                            )
                    )
                }
            }
        }
    }
}

/** Number stepper used for cycle length, sleep hours etc. */
@Composable
fun NiceStepper(
    value: Int,
    min: Int,
    max: Int,
    accent: Color = Purple,
    suffix: String = "",
    onChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        StepperButton("−", accent, enabled = value > min) { onChange((value - 1).coerceAtLeast(min)) }
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .widthIn(min = 74.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    com.dastyar.app.data.Dates.fa(value.toString()),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                if (suffix.isNotBlank()) {
                    Text(
                        suffix,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        StepperButton("+", accent, enabled = value < max) { onChange((value + 1).coerceAtMost(max)) }
    }
}

@Composable
private fun StepperButton(text: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (enabled) accent.copy(alpha = .15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)
        )
    }
}
