package com.dastyar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.Dates

/**
 * Soft brand-tinted card used across the app. When [accent] is set it keeps the
 * short coloured bar the questionnaire uses, so every card reads as one family.
 */
@Composable
fun DastyarCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .cardEnter()
            .clip(Shape.card)
            .background(MaterialTheme.colorScheme.surface)
            .cardOutline(accent)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(20.dp)
    ) {
        Column {
            if (accent != null) {
                Box(
                    Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
                Spacer(Modifier.height(14.dp))
            }
            content()
        }
    }
}

/**
 * Header used by screens that need a title block without the gradient banner
 * (chat and settings keep a compact back bar).
 */
@Composable
fun GradientHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val emoji = title.takeWhile { it.code > 0x2000 }.trim()
    val text = title.removePrefix(emoji).trim()
    ScreenHeader(emoji.ifBlank { "🌱" }, text.ifBlank { title }, subtitle, modifier)
}

@Composable
fun SectionTitle(text: String, emoji: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$emoji $text".trim(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(Shape.inner)
            .background(
                if (enabled) BrandBrush
                else androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color.Gray.copy(alpha = .4f), Color.Gray.copy(alpha = .4f))
                )
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

/**
 * Dashboard stat tile. A coloured emoji badge, a label, a big value and a sub
 * line — the same card language as the questionnaire.
 */
@Composable
fun StatTile(
    emoji: String,
    label: String,
    value: String,
    sub: String = "",
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .cardEnter()
            .clip(Shape.card)
            .background(MaterialTheme.colorScheme.surface)
            .cardOutline(accent)
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(Shape.badge)
                        .background(accent.copy(alpha = .16f)),
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = 18.sp) }
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    sub,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
