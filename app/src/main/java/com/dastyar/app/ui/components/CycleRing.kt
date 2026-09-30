package com.dastyar.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Health
import com.dastyar.app.data.Profile
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Rose

/**
 * The menstrual-cycle ring. A full, unbroken 360° circle where one lap equals
 * one whole cycle: day 1 sits at the top and the days run clockwise. Every
 * single day of the cycle gets its own small dot, so a 20-day cycle shows 20
 * dots, a 28-day cycle shows 28, and so on — the count is never hard-coded.
 *
 * The coloured phases (period, ovulation, PMS) are all derived from the user's
 * own cycle length and period length through [Health.cycleWindows]. Ovulation
 * and PMS are estimates, not medical certainties.
 *
 * The user can change the cycle length and the period length right here; both
 * are persisted through the profile and the ring recalculates immediately.
 */
@Composable
fun CycleRing(
    profile: Profile?,
    cycleDay: Int,
    onStartPeriod: () -> Unit,
    onSetCycleLength: (Int) -> Unit,
    onSetPeriodDays: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val windows = Health.cycleWindows(profile)
    val hasData = windows != null && cycleDay > 0
    val length = windows?.length ?: (profile?.cycleLength ?: 28).coerceIn(15, 60)
    val periodDays = windows?.periodEnd ?: (profile?.periodDays ?: 5).coerceIn(1, 12)

    val periodColor = Rose
    val ovColor = Cyan
    val pmsColor = Amber
    val normalColor = MaterialTheme.colorScheme.surfaceVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .40f)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Thinner band than before, so the ring reads as a fine ring.
                val stroke = size.minDimension * 0.055f
                val dotR = stroke * 0.24f
                val inset = stroke / 2f + size.minDimension * 0.03f
                val d = size.minDimension - inset * 2f
                val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                val arcSize = Size(d, d)
                val r = d / 2f
                val cX = topLeft.x + r
                val cY = topLeft.y + r
                val perDay = 360f / length
                // A day's dot sits at the CENTRE of that day's slice, so the
                // full circle is covered evenly with no gap or overlap.
                fun angleFor(day: Int) = Math.toRadians((-90.0 + (day - 0.5) * perDay))
                fun pointFor(day: Int): Offset {
                    val a = angleFor(day)
                    return Offset(cX + (r * Math.cos(a)).toFloat(), cY + (r * Math.sin(a)).toFloat())
                }

                // Unbroken base track.
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )

                if (hasData && windows != null) {
                    val w = windows
                    fun drawRange(startDay: Int, endDay: Int, color: Color) {
                        if (endDay < startDay) return
                        // Draw from the edge of the first day's slice to the edge
                        // of the last day's slice so adjacent phases meet exactly.
                        val start = -90f + (startDay - 1) * perDay
                        val sweep = (endDay - startDay + 1) * perDay
                        drawArc(
                            color = color,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                    }
                    // Neutral days first, then the coloured phases on top.
                    drawRange(1, length, normalColor)
                    drawRange(w.periodEnd + 1, w.ovulationStart - 1, normalColor)
                    drawRange(w.ovulationEnd + 1, w.pmsStart - 1, normalColor)
                    drawRange(1, w.periodEnd, periodColor)
                    drawRange(w.ovulationStart, w.ovulationEnd, ovColor)
                    drawRange(w.pmsStart, w.pmsEnd, pmsColor)

                    // One small dot per day of the cycle — the day number is
                    // readable from its position (day 1 at the top).
                    for (day in 1..length) {
                        val p = pointFor(day)
                        drawCircle(Color.White.copy(alpha = .95f), radius = dotR, center = p)
                    }
                }

                // Current-day marker: a small ring that hugs the day's own dot,
                // with the day number drawn inside it. It never covers the band.
                if (hasData) {
                    val mp = pointFor(cycleDay)
                    val mr = stroke * 0.78f
                    drawCircle(Color.White, radius = mr, center = mp)
                    drawCircle(periodColor, radius = mr, center = mp, style = Stroke(width = 2.5f))
                }
            }

            // Centre content, drawn as normal composables on top of the canvas.
            Column(
                Modifier.padding(horizontal = 54.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    Dates.pretty(Dates.today()),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                if (hasData) {
                    Text(
                        if (cycleDay == 1) "روز اول پریود" else "روز ${Dates.fa(cycleDay)} چرخه",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Health.phaseLabel(profile)?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, fontSize = 11.5.sp, color = periodColor, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        "چرخه‌ات را ثبت کن",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "تاریخ شروع پریود را وارد کن",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(10.dp))
                // The real button: opens the existing period date picker.
                Box(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onStartPeriod() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🩸", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (hasData) "ویرایش پریود" else "شروع پریود",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        CycleLegend(periodColor = periodColor, ovColor = ovColor, pmsColor = pmsColor)

        Spacer(Modifier.height(14.dp))
        // ---- cycle length control (15..60 days) ----
        StepperRow(
            title = "طول چرخه",
            valueLabel = "${Dates.fa(length)} روز",
            onMinus = { if (length > 15) onSetCycleLength(length - 1) },
            onPlus = { if (length < 60) onSetCycleLength(length + 1) }
        )
        Spacer(Modifier.height(10.dp))
        // ---- period length control (1..min(12, length) days) ----
        val maxPeriod = minOf(12, length)
        StepperRow(
            title = "مدت پریود",
            valueLabel = "${Dates.fa(periodDays)} روز",
            onMinus = { if (periodDays > 1) onSetPeriodDays(periodDays - 1) },
            onPlus = { if (periodDays < maxPeriod) onSetPeriodDays(periodDays + 1) }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "بازه تخمک‌گذاری و PMS تخمینی است و از طول چرخه تو محاسبه می‌شود، نه یک مقدار قطعی پزشکی.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StepperRow(
    title: String,
    valueLabel: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 13.sp, modifier = Modifier.weight(1f))
        StepperButton("−", onMinus)
        Text(
            valueLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp)
        )
        StepperButton("+", onPlus)
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CycleLegend(periodColor: Color, ovColor: Color, pmsColor: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem("پریود", periodColor)
        LegendDivider()
        LegendItem("تخمک‌گذاری", ovColor)
        LegendDivider()
        LegendItem("PMS", pmsColor)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(11.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp)
    }
}

@Composable
private fun LegendDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(16.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
