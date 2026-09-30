package com.dastyar.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.Cycle
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Health
import com.dastyar.app.data.Profile
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.ui.theme.Rose

/**
 * The angle, in degrees, where the first day of the cycle begins. The fixed
 * visual gap is centred on the top, so day 1 starts just to the right of it.
 * Shared by the drawing code and the tap hit-testing so they can never drift.
 */
private fun gapDegOf(length: Int): Float =
    360f * (Cycle.RING_GAP_DAYS / (length + Cycle.RING_GAP_DAYS))

private fun startAngleOf(length: Int): Float = -90f + gapDegOf(length) / 2f

/**
 * The menstrual-cycle ring.
 *
 * It is a ring, not a fully closed circle: a fixed gap of about
 * [Cycle.RING_GAP_DAYS] days of visual space is always left at the top, so the
 * first day of the period and the last day of the cycle are visibly separated
 * instead of running into each other. The gap is a fixed *visual* size — it is
 * the same on a 25-day cycle and a 35-day cycle — and every real day is spread
 * evenly across the rest of the circumference.
 *
 * One lap still equals one whole cycle, day 1 sits just after the top gap and
 * the days run clockwise. Every single day gets its own small dot, so a 20-day
 * cycle shows 20 dots, a 28-day cycle shows 28, and so on — the count is never
 * hard-coded.
 *
 * The coloured phases (period, ovulation, PMS) are all derived from the user's
 * own cycle length and period length through [Health.cycleWindows]. Ovulation
 * and PMS are estimates, not medical certainties.
 *
 * The user can change the cycle length and the period length right here, and
 * can record the start and the end of a period. Both are persisted through the
 * profile/history and the ring recalculates immediately.
 */
@Composable
fun CycleRing(
    profile: Profile?,
    cycleDay: Int,
    info: Cycle.Info?,
    onStartPeriod: () -> Unit,
    onEndPeriod: () -> Unit,
    onSetCycleLength: (Int) -> Unit,
    onSetPeriodDays: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Which past day the user tapped, if any. Only days from the start of the
    // cycle up to today can be picked; the future is deliberately not tappable.
    var selectedDay by remember(info?.startIso, info?.length) { mutableStateOf<Int?>(null) }
    // The furthest day the user may inspect: today's real position in the cycle.
    val reachableDay = if (info != null && info.hasCycle) Cycle.markerDay(info) else 0
    // Prefer the lengths learned from the real history; fall back to the
    // profile's declared values when there is not enough history yet.
    val windows = if (info != null && info.hasCycle) {
        Health.cycleWindows(info.startIso, info.length, info.periodDays)
    } else {
        Health.cycleWindows(profile)
    }
    val hasData = windows != null && cycleDay > 0
    val length = windows?.length ?: (profile?.cycleLength ?: 28).coerceIn(15, 60)
    val periodDays = windows?.periodEnd ?: (profile?.periodDays ?: 5).coerceIn(1, 12)
    val overdue = Cycle.isOverdue(info)
    val overdueDays = Cycle.overdueDays(info)
    // The marker uses the clamped day, so an overdue cycle parks on the last
    // day instead of silently wrapping to day 1.
    val markerDay = if (hasData) Cycle.markerDay(info).coerceAtLeast(1) else 0

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
            Canvas(
                Modifier
                    .fillMaxSize()
                    // Tapping the ring picks the nearest past day so the user
                    // can see what day it was. Future days are not selectable:
                    // anything past today's real position is ignored.
                    .pointerInput(reachableDay, length, startAngleOf(length)) {
                        detectTapGestures { tap ->
                            if (reachableDay <= 0) return@detectTapGestures
                            val minDim = size.minDimension.toFloat()
                            val strokePx = minDim * 0.055f
                            val insetPx = strokePx / 2f + minDim * 0.03f
                            val dPx = minDim - insetPx * 2f
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = tap.x - cx
                            val dy = tap.y - cy
                            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                            // Only taps near the ring band count, so the centre
                            // buttons stay usable.
                            if (dist < dPx / 2f - strokePx * 1.6f ||
                                dist > dPx / 2f + strokePx * 1.6f
                            ) return@detectTapGestures
                            val deg = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble())
                            val start = startAngleOf(length).toDouble()
                            val rel = (deg - start + 360.0) % 360.0
                            val usable = 360.0 - gapDegOf(length).toDouble()
                            // A tap inside the top gap is not a day: ignore it.
                            if (rel >= usable) return@detectTapGestures
                            val day = (rel / usable * length).toInt() + 1
                            val clamped = day.coerceIn(1, reachableDay)
                            selectedDay = if (selectedDay == clamped) null else clamped
                        }
                    }
            ) {
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

                // ---- geometry of the open ring -------------------------------
                // The full 360° is shared between the real days and the fixed
                // visual gap at the top. The gap takes the same share of the
                // circumference on every cycle, so it is always the same size
                // on screen regardless of how long the cycle is.
                val gapDeg = gapDegOf(length)
                val usableDeg = 360f - gapDeg
                val perDay = usableDeg / length
                // The gap is centred on the top (12 o'clock), so the usable arc
                // runs clockwise from just right of the gap all the way round
                // to just left of it.
                val startAngle = startAngleOf(length)

                // A day's dot sits at the CENTRE of that day's slice.
                fun angleFor(day: Int) =
                    Math.toRadians((startAngle + (day - 0.5) * perDay).toDouble())
                fun pointFor(day: Int): Offset {
                    val a = angleFor(day)
                    return Offset(cX + (r * Math.cos(a)).toFloat(), cY + (r * Math.sin(a)).toFloat())
                }
                // Arc angles for a run of whole days, measured in the same
                // clockwise system as the dots.
                fun arcStart(day: Int) = startAngle + (day - 1) * perDay
                fun arcSweep(fromDay: Int, toDay: Int) = (toDay - fromDay + 1) * perDay

                // The base track is drawn as the usable arc only: the top gap
                // stays empty, which is what makes the break visible.
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = usableDeg,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )

                if (hasData && windows != null) {
                    val w = windows
                    fun drawRange(startDay: Int, endDay: Int, color: Color) {
                        if (endDay < startDay) return
                        drawArc(
                            color = color,
                            startAngle = arcStart(startDay),
                            sweepAngle = arcSweep(startDay, endDay),
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
                    // readable from its position (day 1 just after the gap).
                    for (day in 1..length) {
                        val p = pointFor(day)
                        drawCircle(Color.White.copy(alpha = .95f), radius = dotR, center = p)
                    }
                }

                // Current-day marker: a small ring that hugs the day's own dot,
                // with the day number drawn inside it. It never covers the band.
                if (hasData && markerDay > 0) {
                    val mp = pointFor(markerDay)
                    val mr = stroke * 0.78f
                    drawCircle(Color.White, radius = mr, center = mp)
                    drawCircle(
                        if (overdue) Amber else periodColor,
                        radius = mr, center = mp, style = Stroke(width = 2.5f)
                    )
                }

                // The day the user tapped, highlighted with a soft filled dot so
                // it is obvious which past day the label below refers to.
                selectedDay?.let { sd ->
                    if (sd in 1..length) {
                        val sp = pointFor(sd)
                        drawCircle(Purple.copy(alpha = .30f), radius = stroke * 0.95f, center = sp)
                        drawCircle(Purple, radius = stroke * 0.42f, center = sp)
                    }
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
                        when {
                            overdue -> "چرخه از موعد گذشته"
                            cycleDay == 1 -> "روز اول پریود"
                            else -> "روز ${Dates.fa(cycleDay)} چرخه"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    if (overdue) {
                        Text(
                            "${Dates.fa(overdueDays)} روز تأخیر — منتظر شروع پریودی",
                            fontSize = 11.5.sp,
                            color = Amber,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Health.phaseLabel(profile)?.let {
                            Text(it, fontSize = 11.5.sp, color = periodColor, fontWeight = FontWeight.Bold)
                        }
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
                // Info label for the tapped past day. Purely informational: it
                // says which day of the cycle it was, its date and its phase.
                selectedDay?.let { sd ->
                    if (hasData && sd in 1..length) {
                        val dateIso = info?.startIso?.let { Dates.plusDays(it, sd - 1) }
                        val phaseLabel = when {
                            sd <= periodDays -> "مرحله قاعدگی"
                            sd < Health.ovulationStart(length) -> "مرحله فولیکولی"
                            sd <= Health.ovulationStart(length) + 3 -> "مرحله تخمک‌گذاری"
                            else -> "مرحله لوتئال"
                        }
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Purple.copy(alpha = .12f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (sd == reachableDay) "امروز — روز ${Dates.fa(sd)} چرخه"
                                else "روز ${Dates.fa(sd)} چرخه",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Purple,
                                textAlign = TextAlign.Center
                            )
                            if (dateIso != null) {
                                Text(
                                    Dates.pretty(dateIso),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                phaseLabel,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "برای بستن، دوباره روی همان روز بزن",
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                // Two real actions: start a period (opens the date picker) and,
                // once a period is running, mark the day it ended.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                if (hasData) "ویرایش / شروع پریود" else "شروع پریود",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (hasData) {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onEndPeriod() }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✅", fontSize = 13.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "پایان پریود",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
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
