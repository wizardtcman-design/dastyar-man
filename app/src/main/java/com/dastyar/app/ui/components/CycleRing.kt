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
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Rose

/**
 * The menstrual-cycle ring. A full 360° circle where one lap equals one whole
 * cycle: day 1 sits at the top and the days run clockwise. Each segment is
 * coloured from the user's real cycle data (period, ovulation, PMS, and plain
 * days in between), and the current day is marked with a bubble on the ring.
 *
 * When there is no cycle data the ring is drawn in a single neutral colour and
 * the centre asks for a start date instead of inventing numbers.
 */
@Composable
fun CycleRing(
    profile: com.dastyar.app.data.Profile?,
    cycleDay: Int,
    onStartPeriod: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windows = Health.cycleWindows(profile)
    val hasData = windows != null && cycleDay > 0
    val length = windows?.length ?: 28

    val periodColor = Rose
    val ovColor = Cyan
    val pmsColor = Amber
    val normalColor = MaterialTheme.colorScheme.surfaceVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.085f
                val inset = stroke / 2f + size.minDimension * 0.02f
                val d = size.minDimension - inset * 2f
                val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                val arcSize = Size(d, d)

                // Base track.
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                if (hasData && windows != null) {
                    // Sweep per day so the whole cycle always fills exactly 360°,
                    // whatever the cycle length is. Day 1 begins at the top
                    // (-90°) and the ring grows clockwise.
                    val perDay = 360f / length
                    fun drawRange(startDay: Int, endDay: Int, color: Color) {
                        if (endDay < startDay) return
                        val start = -90f + (startDay - 1) * perDay
                        val sweep = (endDay - startDay + 1) * perDay
                        drawArc(
                            color = color,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    // Order matters: draw the neutral "normal" days first, then
                    // the coloured windows on top so they are never hidden.
                    val w = windows
                    drawRange(1, length, normalColor)
                    drawRange(w.periodEnd + 1, w.ovulationStart - 1, normalColor)
                    drawRange(w.ovulationEnd + 1, w.pmsStart - 1, normalColor)
                    drawRange(1, w.periodEnd, periodColor)
                    drawRange(w.ovulationStart, w.ovulationEnd, ovColor)
                    drawRange(w.pmsStart, w.pmsEnd, pmsColor)

                    // Small dots inside the coloured segments, like the reference.
                    fun dotDays(start: Int, end: Int) {
                        val step = ((end - start + 1) / 4).coerceAtLeast(1)
                        var day = start
                        while (day <= end) {
                            val ang = Math.toRadians((-90f + (day - 0.5f) * perDay).toDouble())
                            val r = d / 2f
                            val cx = topLeft.x + r + (r * Math.cos(ang)).toFloat()
                            val cy = topLeft.y + r + (r * Math.sin(ang)).toFloat()
                            drawCircle(Color.White.copy(alpha = .9f), radius = stroke * 0.14f, center = Offset(cx, cy))
                            day += step
                        }
                    }
                    dotDays(w.ovulationStart, w.ovulationEnd)
                    dotDays(w.pmsStart, w.pmsEnd)

                    // Current-day marker bubble sitting on the ring.
                    val markerAngle = Math.toRadians((-90f + (cycleDay - 0.5f) * perDay).toDouble())
                    val r = d / 2f
                    val mx = topLeft.x + r + (r * Math.cos(markerAngle)).toFloat()
                    val my = topLeft.y + r + (r * Math.sin(markerAngle)).toFloat()
                    drawCircle(Color.White, radius = stroke * 0.92f, center = Offset(mx, my))
                    drawCircle(periodColor, radius = stroke * 0.92f, center = Offset(mx, my), style = Stroke(width = 4f))
                }
            }

            // Centre content, drawn as normal composables on top of the canvas.
            Column(
                Modifier.padding(horizontal = 46.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    Dates.pretty(com.dastyar.app.data.Dates.today()),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                if (hasData) {
                    Text(
                        if (cycleDay == 1) "روز اول پریود" else "روز ${Dates.fa(cycleDay)} چرخه",
                        fontSize = 19.sp,
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
                        fontSize = 17.sp,
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
                Spacer(Modifier.height(12.dp))
                // The real button: opens the existing period date picker.
                Box(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onStartPeriod() }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🩸", fontSize = 15.sp)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (hasData) "ویرایش پریود" else "شروع پریود",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        CycleLegend(periodColor = periodColor, ovColor = ovColor, pmsColor = pmsColor)
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
