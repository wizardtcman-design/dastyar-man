package com.dastyar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.CheckIn
import com.dastyar.app.data.DailySuggestion
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Health
import com.dastyar.app.data.Profile
import com.dastyar.app.data.WeightEntry
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.*

private enum class Range(val label: String, val days: Int) {
    D7("۷ روز", 7), D30("۳۰ روز", 30), D90("۳ ماه", 90)
}

/**
 * The dashboard. Ordered by how much it matters day to day:
 * greeting, today's practical plan, today's status, body/cycle, then trends.
 * Everything shown comes from data the user actually recorded.
 */
@Composable
fun HomeScreen(vm: MainViewModel, onOpenCheckIn: () -> Unit, needsCheckIn: Boolean) {
    val profile by vm.profile.collectAsState()
    val checkIns by vm.checkIns.collectAsState()
    val today by vm.todayCheckIn.collectAsState()
    val suggestion by vm.suggestion.collectAsState()
    val loading by vm.loadingSuggestion.collectAsState()
    val weights by vm.weights.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(Range.D7) }
    var metric by remember { mutableStateOf("انرژی") }

    if (showSettings) {
        SettingsScreen(vm, onClose = { showSettings = false })
        return
    }

    val name = profile?.firstName?.ifBlank { "دوست من" } ?: "دوست من"
    val waterGoal = Health.waterTarget(profile, today)
    val water = today?.waterGlasses ?: 0
    val energyPct = energyPercent(today)
    val cycleDay = Health.cycleDay(profile)
    val localPlan = remember(profile, today, checkIns) { vm.localPlan() }
    val conditionInfo by vm.conditionInfo.collectAsState()
    val loadingCondition by vm.loadingCondition.collectAsState()

    LaunchedEffect(profile?.medicalConditions) {
        vm.loadConditionInfo()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------------------------------------------------------- greeting
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ScreenHeader(
                    emoji = greetingEmoji(),
                    title = greetingFor(name),
                    subtitle = Dates.pretty(vm.today) +
                            if (cycleDay > 0) " • روز ${Dates.fa(cycleDay)} چرخه" else "",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "تنظیمات")
                }
            }
        }

        // ------------------------------------------------- check-in reminder
        if (needsCheckIn) {
            item {
                DastyarCard(onClick = onOpenCheckIn, accent = Amber) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(Amber.copy(alpha = .16f)),
                            contentAlignment = Alignment.Center
                        ) { Text("📝", fontSize = 21.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("وضعیت امروزت ثبت نشده", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "چند سؤال کوتاه — کمتر از یک دقیقه",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Tag("ثبت کن", Amber)
                    }
                }
            }
        }

        // ------------------------------------------------------ today status
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "⚡", "انرژی امروز", "${Dates.fa(energyPct)}٪",
                    energyHint(today), Purple, Modifier.weight(1f)
                )
                StatTile(
                    "😴", "خواب دیشب", sleepLabel(today),
                    today?.sleepQuality?.ifBlank { "ثبت نشده" } ?: "ثبت نشده",
                    Cyan, Modifier.weight(1f)
                )
            }
        }

        item {
            WaterCard(
                water = water,
                goal = waterGoal,
                onAdd = { vm.updateWater(1) },
                onRemove = { vm.updateWater(-1) }
            )
        }

        // ------------------------------------------------------- body & cycle
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BmiTile(
                    profile = profile,
                    weights = weights,
                    onUpdate = { showWeightDialog = true },
                    modifier = Modifier.weight(1f)
                )
                CycleTile(
                    cycleDay = cycleDay,
                    subtitle = cycleSubtitle(profile, today),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ---------------------------------------------------- today's plan
        item {
            PlanCard(
                suggestion = suggestion,
                localPlan = localPlan,
                loading = loading,
                onRefresh = { vm.generateSuggestion(true) }
            )
        }

        // ------------------------------------------- declared condition card
        if (profile?.medicalConditions?.isNotBlank() == true) {
            item {
                ConditionCard(
                    conditions = profile?.medicalConditions.orEmpty(),
                    info = conditionInfo,
                    loading = loadingCondition,
                    onRefresh = { vm.loadConditionInfo(true) }
                )
            }
        }

        // ----------------------------------------------------------- trends
        item {
            DastyarCard(accent = Purple) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Purple.copy(alpha = .16f)),
                        contentAlignment = Alignment.Center
                    ) { Text("📊", fontSize = 19.sp) }
                    Spacer(Modifier.width(10.dp))
                    Text("روند وضعیت", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(14.dp))
                SingleChoiceChips(
                    options = Range.entries.map { it.label },
                    selected = range.label,
                    accent = Purple
                ) { picked ->
                    Range.entries.firstOrNull { it.label == picked }?.let { range = it }
                }
                Spacer(Modifier.height(10.dp))
                val metrics = listOf("انرژی", "خواب", "آب", "پوست", "بی‌رمقی")
                SingleChoiceChips(options = metrics, selected = metric, accent = Purple) { metric = it }
                Spacer(Modifier.height(18.dp))

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
                        "برای دیدن نمودار، چند روز وضعیتت را ثبت کن. " +
                                "فقط داده‌های واقعی تو نمایش داده می‌شود.",
                        fontSize = 12.5.sp,
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
                    metricAverage(metric, points)?.let { avg ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "میانگین $avg",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            DastyarCard {
                Text(
                    "همه اعداد این صفحه از داده‌های ثبت‌شده خودت ساخته می‌شوند و شاخص آماری‌اند، " +
                            "نه اندازه‌گیری پزشکی. در صورت وجود علائم هشدار به پزشک مراجعه کن.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }

    if (showWeightDialog) {
        WeightDialog(
            current = profile?.weightKg ?: 0f,
            onDismiss = { showWeightDialog = false },
            onSave = { kg ->
                vm.recordWeight(kg)
                showWeightDialog = false
            }
        )
    }
}

// ------------------------------------------------------------------- cards

/**
 * Dashboard card for the condition the user declared. The explanation is
 * generated by the AI and clearly framed as education, never diagnosis; the
 * user's own words always stay visible.
 */
@Composable
private fun ConditionCard(
    conditions: String,
    info: String?,
    loading: Boolean,
    onRefresh: () -> Unit
) {
    DastyarCard(accent = Amber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Amber.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Text("🩺", fontSize = 19.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("شرایط ثبت‌شده تو", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(conditions, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "توضیح دوباره")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (info.isNullOrBlank()) {
            Text(
                "توضیح کوتاه و آموزشی این شرایط را می‌سازم…",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            info.lines().filter { it.isNotBlank() }.forEach { raw ->
                val line = Dates.fa(raw)
                val parts = line.split("|", limit = 2)
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    if (parts.size == 2) {
                        Text(parts[0].trim(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            parts[1].trim(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(line, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "این توضیح آموزشی است، نه تشخیص پزشکی؛ جای نظر پزشک تو را نمی‌گیرد.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Cycle tile, sized like the BMI tile next to it. */
@Composable
private fun CycleTile(cycleDay: Int, subtitle: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(Shape.card)
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(Shape.badge)
                        .background(Pink.copy(alpha = .16f)),
                    contentAlignment = Alignment.Center
                ) { Text("🩷", fontSize = 17.sp) }
                Spacer(Modifier.width(8.dp))
                Text(
                    "چرخه پریود",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                if (cycleDay > 0) "روز ${Dates.fa(cycleDay)}" else "ثبت نشده",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Today's practical plan, from local logic and optionally refined by AI. */
@Composable
private fun PlanCard(
    suggestion: DailySuggestion?,
    localPlan: DailySuggestion?,
    loading: Boolean,
    onRefresh: () -> Unit
) {
    val shown = suggestion ?: localPlan
    DastyarCard(accent = Green) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Green.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Text("🌱", fontSize = 19.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("پیشنهاد امروز برای تو", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "بر اساس داده‌های خودت",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "تولید دوباره")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (shown == null) {
            Text(
                "هنوز داده‌ای برای پیشنهاد نداریم. با ثبت وضعیت امروز، پیشنهادهای شخصی ساخته می‌شود.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            shown.content.lines().filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split("|", limit = 2)
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    if (parts.size == 2) {
                        Text(parts[0].trim(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            parts[1].trim(),
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(line, fontSize = 13.5.sp)
                    }
                }
            }
            if (loading) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "در حال شخصی‌سازی بیشتر…",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Water progress with quick add/remove. */
@Composable
private fun WaterCard(water: Int, goal: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    DastyarCard(accent = Cyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Cyan.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Text("💧", fontSize = 21.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("آب امروز", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "هدف هوشمند: ${Dates.fa(goal)} لیوان",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${Dates.fa(water)} / ${Dates.fa(goal)}",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { if (goal == 0) 0f else (water.toFloat() / goal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)),
            color = MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) { Text("+ یک لیوان") }
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) { Text("−") }
        }
    }
}

/** BMI tile with a trend sparkline from the weight log. */
@Composable
private fun BmiTile(
    profile: Profile?,
    weights: List<WeightEntry>,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bmi = Health.bmi(profile)
    val category = Health.bmiCategory(profile)
    val ageApplies = Health.bmiAdultBandsApply(profile)
    val latest = weights.lastOrNull()

    Box(
        modifier
            .clip(Shape.card)
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onUpdate() }
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(Shape.badge)
                        .background(Green.copy(alpha = .16f)),
                    contentAlignment = Alignment.Center
                ) { Text("⚖️", fontSize = 17.sp) }
                Spacer(Modifier.width(8.dp))
                Text(
                    "قد و وزن",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(9.dp))

            when {
                bmi == null -> {
                    Text("ثبت نشده", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "برای محاسبه BMI بزن",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        "${"%.1f".format(bmi)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (ageApplies && category != null) {
                        Spacer(Modifier.height(3.dp))
                        Tag(category, if (category == "محدوده معمول") Green else Amber)
                    } else {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "دسته‌بندی بزرگسالان برای سن تو مناسب نیست",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    latest?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${"%.1f".format(it.weightKg)} کیلو • ${Dates.pretty(it.date)}",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (weights.size >= 2) {
                Spacer(Modifier.height(10.dp))
                WeightSpark(weights.takeLast(12).map { it.weightKg })
            }
        }
    }
}

/** Tiny weight trend line, drawn from real entries only. */
@Composable
private fun WeightSpark(values: List<Float>) {
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 1f
    val span = (max - min).coerceAtLeast(0.5f)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(30.dp)) {
        val w = size.width
        val h = size.height
        val step = if (values.size > 1) w / (values.size - 1) else w
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = step * i
            val y = h - ((v - min) / span) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Green, style = Stroke(width = 2.5f))
        values.forEachIndexed { i, v ->
            val x = step * i
            val y = h - ((v - min) / span) * h
            drawCircle(Green, radius = 3f, center = Offset(x, y))
        }
    }
}

@Composable
private fun WeightDialog(current: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0f) current.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت وزن امروز ⚖️") },
        text = {
            Column {
                Text(
                    "وزن امروزت را وارد کن تا روند تغییرات در داشبورد ساخته شود.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' }.take(6) },
                    label = { Text("وزن (کیلوگرم)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { text.toFloatOrNull()?.let(onSave) }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// --------------------------------------------------------------- helpers

/** Warm time-of-day greeting that uses the name the user gave us. */
private fun greetingFor(name: String): String {
    val hour = java.time.LocalTime.now().hour
    val part = when (hour) {
        in 5..11 -> "صبح بخیر"
        in 12..16 -> "ظهر بخیر"
        in 17..20 -> "عصر بخیر"
        else -> "شب بخیر"
    }
    return "$part $name جان"
}

private fun greetingEmoji(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "🌤"
    in 12..16 -> "☀️"
    in 17..20 -> "🌇"
    else -> "🌙"
}

private fun cycleSubtitle(profile: Profile?, today: CheckIn?): String {
    if (today?.isPeriodDay == true) return "امروز روز پریوده"
    val until = Health.daysUntilPeriod(profile) ?: return ""
    return if (until <= 0) "دوران قاعدگی" else "${Dates.fa(until)} روز تا پریود بعدی"
}

private fun metricAverage(metric: String, points: List<Float>): String? {
    val real = points.filter { it > 0f }
    if (real.isEmpty()) return null
    val avg = real.average()
    return when (metric) {
        "خواب" -> "${"%.1f".format(avg)} ساعت"
        "آب" -> "${"%.0f".format(avg)} لیوان"
        "انرژی" -> "${"%.0f".format(avg)}٪"
        else -> "${"%.0f".format(avg)}"
    }
}

/** Hand-drawn line chart so the app has no chart-library version risk. */
@Composable
fun LineChart(values: List<Float>, labels: List<String>, color: Color) {
    val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val minV = 0f
    val n = values.size.coerceAtLeast(2)

    Column {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val w = size.width
            val h = size.height
            val pad = 14f

            fun px(i: Int) = pad + (w - 2 * pad) * (i.toFloat() / (n - 1))
            fun py(v: Float) =
                h - pad - (h - 2 * pad) * ((v - minV) / (maxV - minV)).coerceIn(0f, 1f)

            repeat(4) { g ->
                val y = pad + (h - 2 * pad) * (g / 3f)
                drawLine(Color.Gray.copy(alpha = .18f), Offset(pad, y), Offset(w - pad, y), 1f)
            }

            val path = Path()
            values.forEachIndexed { i, v ->
                val x = px(i); val y = py(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 3f))

            values.forEachIndexed { i, v ->
                drawCircle(color, radius = 5f, center = Offset(px(i), py(v)))
                drawCircle(Color.White, radius = 2f, center = Offset(px(i), py(v)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
        "${Dates.fa(hours)}س ${Dates.fa(minutes)}د"
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

/** 0-100: lower fatigue is better, inverted so the chart rises with wellbeing. */
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
