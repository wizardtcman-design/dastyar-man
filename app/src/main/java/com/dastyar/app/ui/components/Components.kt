package com.dastyar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.Dates

/** Soft brand-tinted card used across the app. */
@Composable
fun DastyarCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = MaterialTheme.colorScheme.surface
    val mod = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(22.dp))
        .background(base)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        .padding(18.dp)
    Column(mod) {
        if (accent != null) {
            Box(
                Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun GradientHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionTitle(text: String, emoji: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$emoji $text".trim(), fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

/** A big primary action button with the brand gradient. */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val brush = Brush.horizontalGradient(
        listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) brush else Brush.horizontalGradient(
                listOf(Color.Gray.copy(alpha = .4f), Color.Gray.copy(alpha = .4f))
            ))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Single-choice chip row used by the questionnaire and check-in. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { onSelect(option) }
            ) {
                Text(
                    option,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Progress shown on the onboarding flow. */
@Composable
fun StepProgress(step: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (i <= step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "مرحله ${Dates.fa(step + 1)} از ${Dates.fa(total)}",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun StatTile(
    emoji: String,
    label: String,
    value: String,
    sub: String = "",
    accent: Color,
    modifier: Modifier = Modifier
) {
    DastyarCard(modifier = modifier, accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
