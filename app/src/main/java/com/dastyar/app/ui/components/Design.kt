package com.dastyar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.dastyar.app.ui.theme.Pink
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.ui.theme.Vazir

/**
 * The shared look of the whole app: one gradient banner at the top of every
 * screen, soft rounded cards with a coloured top bar, gradient buttons and a
 * single chip style. The questionnaire came first; everything now matches it.
 */

/** Sizes and radii used everywhere, so screens cannot drift apart. */
object Shape {
    val card = RoundedCornerShape(24.dp)
    val inner = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(15.dp)
    val badge = RoundedCornerShape(15.dp)
}

/**
 * Colour of the thin border drawn around every card. When a card has its own
 * accent the border takes that colour softly, otherwise the theme primary is
 * used, so every card is visibly outlined but nothing shouts.
 */
@Composable
fun cardBorderColor(accent: Color? = null): Color {
    val base = accent ?: MaterialTheme.colorScheme.primary
    return base.copy(alpha = if (accent != null) .45f else .28f)
}

/**
 * Applies the shared thin coloured border to any surface card. Kept in one
 * place so the whole app stays visually consistent.
 */
@Composable
fun Modifier.cardOutline(accent: Color? = null): Modifier =
    this.border(1.dp, cardBorderColor(accent), Shape.card)

/** The brand gradient used by the header, the primary button and the step badge. */
val BrandBrush: Brush get() = Brush.horizontalGradient(listOf(Purple, Pink))

/**
 * Header banner used at the top of every screen. Replaces the old plain-text
 * GradientHeader with the same rounded gradient style as the questionnaire.
 */
@Composable
fun ScreenHeader(
    emoji: String,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(Shape.card)
            .background(BrandBrush)
    ) {
        // soft light blobs so the gradient never looks flat
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 26.dp, y = (-30).dp)
                .size(120.dp)
                .clip(RoundedCornerShape(60.dp))
                .background(Color.White.copy(alpha = .10f))
        )
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(Shape.badge)
                        .background(Color.White.copy(alpha = .22f)),
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = 22.sp) }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = .92f),
                    fontSize = 12.5.sp,
                    fontFamily = Vazir
                )
            }
        }
    }
}

/**
 * Section heading with an emoji badge, matching the question cards. Wrap any
 * group of content so every screen reads the same way.
 */
@Composable
fun SectionCard(
    emoji: String,
    title: String,
    hint: String? = null,
    accent: Color = Purple,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(Shape.card)
            .background(MaterialTheme.colorScheme.surface)
            .cardOutline(accent)
            .padding(20.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(Shape.badge)
                        .background(accent.copy(alpha = .16f)),
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = 21.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (hint != null) {
                        Text(
                            hint,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/**
 * The centred question card from the questionnaire — a big emoji badge, the
 * question, an optional hint and the answer area.
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
            .clip(Shape.card)
            .background(MaterialTheme.colorScheme.surface)
            .cardOutline(accent)
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(Shape.badge)
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

/** A small pill label, e.g. "امروز" or a status tag. */
@Composable
fun Tag(text: String, accent: Color = Purple) {
    Box(
        Modifier
            .clip(Shape.badge)
            .background(accent.copy(alpha = .16f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}
