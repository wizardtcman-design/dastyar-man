package com.dastyar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.CheckIn
import com.dastyar.app.data.Dates
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.*

private enum class Range(val label: String, val days: Int) {
    D7("۷ روز", 7), D30("۳۰ روز", 30), D90("۳ ماه", 90)
}

@Composable
fun HomeScreen(vm: MainViewModel, onOpenCheckIn: () -> Unit, needsCheckIn: Boolean) {
    val profile by vm.profile.collectAsState()
    val checkIns by vm.checkIns.collectAsState()
    val today by vm.todayCheckIn.collectAsState()
    val suggestion by vm.suggestion.collectAsState()
    val loading by vm.loadingSuggestion.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(Range.D7) }
    var metric by remember { mutableStateOf("انرژی") }

    if (showSettings) {
        SettingsScreen(vm, onClose = { showSettings = false })
        return
    }

    val name = profile?.firstName?.ifBlank { "دوست من" } ?: "دوست من"
    val goal = vm.waterGoal(profile, today)
    val water = today?.waterGlasses ?: 0
    val energyPct = energyPercent(today)
    val cycleDay = profile?.let {
        if (it.lastPeriodDate.isBlank()) 0
        else Dates.cycleDay(it.lastPeriodDate, it.cycleLength)
    } ?: 0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "خوش اومدی $name جان 🌱",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "من دستیار تو هستم.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "تنظیمات")
                }
            }
        }

        if (needsCheckIn) {
            item {
                DastyarCard(onClick = onOpenCheckIn, accent = Amber) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📝", fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("وضعیت امروزت ثبت نشده", fontWeight = FontWeight.Bold)
                            Text(
                                "چند سؤال کوتاه — کمتر از یک دقیقه",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("ثبت ←", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("⚡", "انرژی امروز", "${Dates.fa(energyPct)}٪", energyHint(today), Purple, Modifier.weight(1f))
                StatTile("😴", "خواب", sleepLabel(today), today?.sleepQuality ?: "ثبت نشده", Cyan, Modifier.weight(1f))
            }
        }

        item {
            DastyarCard(accent = Cyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💧", fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("آب امروز", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${Dates.fa(water)} / ${Dates.fa(goal)} لیوان",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (goal == 0) 0f else (water.toFloat() / goal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { vm.updateWater(1) },
                        modifier = Modifier.weight(1f)
                    ) { Text("+ یک لیوان") }
                    OutlinedButton(
                        onClick = { vm.updateWater(-1) },
                        modifier = Modifier.weight(1f)
                    ) { Text("−") }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "🩷", "چرخه پریود",
                    if (cycleDay > 0) "روز ${Dates.fa(cycleDay)}" else "ثبت نشده",
                    if (today?.isPeriodDay == true) "امروز روز پریوده" else
                        profile?.let {
                            if (it.lastPeriodDate.isBlank()) ""
                            else "${Dates.fa(Dates.daysUntilNextPeriod(it.lastPeriodDate, it.cycleLength))} روز تا پریود بعدی"
                        } ?: "",
                    Pink, Modifier.weight(1f)
                )
                StatTile(
                    "✨", "وضعیت پوست",
                    today?.skinStatus?.ifBlank { "ثبت نشده" } ?: "ثبت نشده",
                    today?.acneCount?.take(18) ?: "",
                    Amber, Modifier.weight(1f)
                )
            }
        }

        item {
            DastyarCard(accent = Rose) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("بی‌رمقی", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        today?.fatigueSeverity?.ifBlank { "ثبت نشده" } ?: "ثبت نشده",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // ---- AI daily suggestions ----
        item {
            DastyarCard(accent = Green) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("پیشنهاد امروز برای تو", "🌱")
                    Spacer(Modifier.weight(1f))
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { vm.generateSuggestion(true) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "تولید دوباره")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (suggestion.isNullOrBlank()) {
                    Text(
                        "برای دریافت پیشنهادهای شخصی امروز، روی دکمه زیر بزن.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.generateSuggestion(false) },
                        enabled = !loading
                    ) { Text("دریافت پیشنهاد امروز") }
                } else {
                    SuggestionLines(suggestion!!)
                }
            }
        }

        // ---- chart ----
        item {
            DastyarCard(accent = Purple) {
                SectionTitle("نمودار وضعیت", "📊")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Range.entries.forEach { r ->
                        FilterChip(
                            selected = range == r,
                            onClick = { range = r },
                            label = { Text(r.label, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val metrics = listOf("انرژی", "خواب", "آب", "پوست", "بی‌رمقی")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    metrics.forEach { m ->
                        FilterChip(
                            selected = metric == m,
                            onClick = { metric = m },
                            label = { Text(m, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                val days = Dates.lastDays(range.days)
                val byDate = checkIns.associateBy { it.date }
                val points = days.map { d ->
                    val ci = byDate[d]
                    when (metric) {
                        "انرژی" -> energyPercent(ci).toFloat()
                        "خواب" -> ci?.sleepHours ?: 0f
                        "آب" -> (ci?.waterGlasses ?: 0).toFloat()
                        "پوست" -> skinScore(ci).toFloat()
                        "بی‌رمقی" -> fatigueScore(ci).toFloat()
                        else -> 0f
                    }
                }

                if (points.all { it == 0f }) {
                    Text(
                        "هنوز داده‌ای ثبت نشده. با ثبت وضعیت روزانه نمودار ساخته می‌شود.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LineChart(
                        values = points,
                        labels = days.map { Dates.shortLabel(it) },
                        color = when (metric) {
                            "انرژی" -> Purple
                            "خواب" -> Cyan
                            "آب" -> Color(0xFF38BDF8)
                            "پوست" -> Amber
                            else -> Rose
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "فقط داده‌های واقعی ثبت‌شده توسط تو نمایش داده می‌شود.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            DastyarCard {
                Text(
                    "این اعداد شاخص‌هایی هستند که از پاسخ‌های خودت محاسبه می‌شوند و " +
                            "اندازه‌گیری واقعی بدن نیستند.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun SuggestionLines(text: String) {
    text.lines().filter { it.isNotBlank() }.forEach { line ->
        val parts = line.split("|", limit = 2)
        Row(
            Modifier.padding(vertical = 5.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (parts.size == 2) {
                Text(parts[0].trim(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    parts[1].trim(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(line, fontSize = 14.sp)
            }
        }
    }
}

/** Hand-drawn line chart so the app has no chart-library version risk. */
@Composable
fun LineChart(values: List<Float>, labels: List<String>, color: Color) {
    val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val minV = 0f
    val n = values.size.coerceAtLeast(2)

    Column {
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            val w = size.width
            val h = size.height
            val pad = 14f

            fun px(i: Int) = pad + (w - 2 * pad) * (i.toFloat() / (n - 1))
            fun py(v: Float) =
                h - pad - (h - 2 * pad) * ((v - minV) / (maxV - minV)).coerceIn(0f, 1f)

            // grid
            repeat(4) { g ->
                val y = pad + (h - 2 * pad) * (g / 3f)
                drawLine(
                    Color.Gray.copy(alpha = .18f),
                    Offset(pad, y), Offset(w - pad, y), strokeWidth = 1f
                )
            }

            val path = Path()
            values.forEachIndexed { i, v ->
                val x = px(i)
                val y = py(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 3f))

            values.forEachIndexed { i, v ->
                drawCircle(color, radius = 5f, center = Offset(px(i), py(v)))
                drawCircle(Color.White, radius = 2f, center = Offset(px(i), py(v)))
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = (labels.size / 7).coerceAtLeast(1)
            labels.filterIndexed { i, _ -> i % step == 0 }.forEach {
                Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------- scoring

fun energyPercent(ci: CheckIn?): Int {
    if (ci == null) return 0
    val base = when {
        ci.energyLevel.contains("خیلی خوب") -> 95
        ci.energyLevel.contains("خوب") -> 80
        ci.energyLevel.contains("متوسط") -> 60
        ci.energyLevel.contains("خیلی کم") -> 20
        ci.energyLevel.contains("کم") -> 38
        else -> 60
    }
    var v = base
    when {
        ci.sleepHours >= 7.5 -> v += 5
        ci.sleepHours in 6.0..7.4 -> v += 0
        ci.sleepHours in 4.0..5.99 -> v -= 8
        ci.sleepHours > 0 -> v -= 15
    }
    when (ci.sleepQuality) {
        "عالی" -> v += 5
        "خوب" -> v += 2
        "ضعیف" -> v -= 8
    }
    when (ci.stressLevel) {
        "خیلی زیاد" -> v -= 12
        "زیاد" -> v -= 8
        "متوسط" -> v -= 3
    }
    return v.coerceIn(5, 100)
}

fun sleepLabel(ci: CheckIn?): String {
    val h = ci?.sleepHours ?: 0f
    if (h <= 0f) return "ثبت نشده"
    val hours = h.toInt()
    val minutes = ((h - hours) * 60).toInt()
    return if (minutes > 0)
        "${Dates.fa(hours)} ساعت و ${Dates.fa(minutes)} دقیقه"
    else "${Dates.fa(hours)} ساعت"
}

fun energyHint(ci: CheckIn?): String = when {
    ci == null -> "ثبت نشده"
    energyPercent(ci) >= 75 -> "وضعیت خوبی داری"
    energyPercent(ci) >= 55 -> "قابل قبول"
    else -> "به استراحت نیاز داری"
}

/** 0-100: higher is better skin condition. */
fun skinScore(ci: CheckIn?): Int {
    if (ci == null) return 0
    var v = when {
        ci.skinStatus.contains("بهتر") -> 80
        ci.skinStatus.contains("مثل قبل") -> 60
        ci.skinStatus.contains("بدتر") -> 35
        else -> 55
    }
    when {
        ci.skinInflammation.contains("زیاد") -> v -= 15
        ci.skinInflammation.contains("متوسط") -> v -= 8
        ci.skinInflammation.contains("کم") -> v -= 3
    }
    if (ci.skinSensitivity.contains("زیاد")) v -= 10
    return v.coerceIn(0, 100)
}

/** 0-100: lower fatigue is better, inverted so the chart rises when feeling well. */
fun fatigueScore(ci: CheckIn?): Int {
    if (ci == null) return 0
    val v = when {
        ci.fatigueSeverity.contains("خیلی کم") -> 90
        ci.fatigueSeverity.contains("کم") -> 75
        ci.fatigueSeverity.contains("متوسط") -> 55
        ci.fatigueSeverity.contains("خیلی زیاد") -> 15
        ci.fatigueSeverity.contains("زیاد") -> 30
        ci.energyLevel.isNotBlank() -> energyPercent(ci)
        else -> 55
    }
    return v.coerceIn(0, 100)
}
