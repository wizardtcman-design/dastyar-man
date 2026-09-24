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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ui.theme.Purple

/**
 * A small gradient progress pill shown at the top of each questionnaire step.
 * The same brand gradient is used by the header banner and the primary button.
 */
@Composable
fun StepBadge(step: Int, total: Int, label: String) {
    val brush = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Shape.card)
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
