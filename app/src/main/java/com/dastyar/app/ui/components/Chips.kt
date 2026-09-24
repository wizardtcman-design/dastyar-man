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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ui.theme.Purple

/**
 * The one chip style used by the whole app. Selected chips fill with the accent
 * colour, lift slightly and keep the text on a single line so Persian words
 * never break letter by letter. [compact] shrinks it for dense grids.
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
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Single-choice chips. Tapping the selected chip again keeps it selected, so a
 * value can never be accidentally cleared — the fix for the pain-level bug.
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

/** Multi-choice chips backed by a comma-joined string list. */
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
                onChange(if (isOn) selected - option else selected + option)
            }
        }
    }
}

/** Turns a stored comma-joined string into a list. */
fun splitMulti(value: String): List<String> =
    if (value.isBlank()) emptyList() else value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** Turns a list back into a stored comma-joined string. */
fun joinMulti(values: List<String>): String = values.joinToString(",")

/**
 * The single-choice chip row used by check-in and every other simple form.
 * Kept as a thin alias so older screens keep working while sharing one style.
 */
@Composable
fun ChoiceChips(
    options: List<String>,
    selected: String,
    accent: Color = Purple,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    SingleChoiceChips(
        options = options,
        selected = selected,
        accent = accent,
        onChange = onSelect
    )
}
