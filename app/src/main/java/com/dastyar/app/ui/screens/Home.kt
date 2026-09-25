package com.dastyar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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
    var selectedMetrics by remember { mutableStateOf(setOf(Metric.ENERGY, Metric.SLEEP)) }

    if (showSettings) {
        SettingsScreen(vm, onClose = { showSettings = false })
        return
    }

    val name = profile?.firstName?.ifBlank { "دوست من" } ?: "دوست من"
    val waterGoal = Health.waterTarget(profile, today)
    val water = today?.waterGlasses ?: 0
    val energyPct = energyPercent(today)
    val cycleDay = Health.cycleDay(profile)
    val cyclePhase = Health.phaseLabel(profile)
    val daysUntilPeriod = Health.daysUntilPeriod(profile)
    val cycleTipLocal = remember(profile, today) { Health.cycleTip(profile, today) }
    val cycleTip by vm.cycleTip.collectAsState()
    val loadingCycleTip by vm.loadingCycleTip.collectAsState()
    val cycleTipError by vm.cycleTipError.collectAsState()
    val skinTip by vm.skinTip.collectAsState()
    val loadingSkinTip by vm.loadingSkinTip.collectAsState()
    val skinSummary = remember(profile, today) { Health.skinSummary(profile, today) }
    val localPlan = remember(profile, today, checkIns) { vm.localPlan() }
    val conditionInfo by vm.conditionInfo.collectAsState()
    val loadingCondition by vm.loadingCondition.collectAsState()
    val conditionError by vm.conditionError.collectAsState()
    val suggestionError by vm.suggestionError.collectAsState()

    LaunchedEffect(profile?.medicalConditions) {
        vm.loadConditionInfo()
    }

    LaunchedEffect(cycleDay, cyclePhase) {
        vm.loadCycleTip()
    }

    LaunchedEffect(skinSummary, today?.date) {
        vm.loadSkinTip()
    }

    // Generate today's suggestion when none is stored yet, so the AI actually
    // runs on a fresh day instead of waiting for a manual refresh.
    LaunchedEffect(profile, today, suggestion) {
        if (suggestion == null && profile?.onboardingDone == true) vm.generateSuggestion()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------------------------------------------------------- greeting
        item {
            GreetingCard(
                name = name,
                emoji = greetingEmoji(),
                dateLabel = Dates.pretty(vm.today),
                cycleDay = cycleDay,
                onSettings = { showSettings = true }
            )
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
                    phase = cyclePhase,
                    daysUntil = daysUntilPeriod,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ------------------------------------ smart tip for today's cycle day
        item {
            CycleTipCard(
                localTip = cycleTipLocal,
                aiTip = cycleTip,
                error = cycleTipError,
                loading = loadingCycleTip,
                hasData = cycleTipLocal != null,
                onRefresh = { vm.loadCycleTip(force = true) }
            )
        }

        // ------------------------------------------------ skin & fatigue status
        item {
            SkinCard(
                summary = skinSummary,
                tip = skinTip,
                loading = loadingSkinTip,
                onRefresh = { vm.loadSkinTip(force = true) }
            )
        }

        item {
            StatTile(
                "🥱", "وضعیت بی‌رمقی",
                today?.fatigueSeverity?.ifBlank { profileHintFatigue(profile) } ?: profileHintFatigue(profile),
                fatigueHint(today),
                Rose, Modifier.fillMaxWidth()
            )
        }

        // ---------------------------------------------------- today's plan
        item {
            PlanCard(
                suggestion = suggestion,
                localPlan = localPlan,
                loading = loading,
                error = suggestionError,
                onRefresh = { vm.generateSuggestion(true) }
            )
        }

        // ------------------------------------------- declared condition card
        if (profile?.medicalConditions?.isNotBlank() == true) {
            item {
                ConditionCard(
                    conditions = profile?.medicalConditions.orEmpty(),
                    info = conditionInfo,
                    error = conditionError,
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

                val days = Dates.lastDays(range.days)
                val byDate = checkIns.associateBy { it.date }
                val byWeight = weights.associateBy { it.date }

                // Raw value of one metric for one day, or null when not recorded.
                // A recorded zero (no sleep logged, no glasses) counts as missing
                // so the chart draws an honest gap instead of a false drop.
                fun rawFor(m: Metric, d: String): Float? = when (m) {
                    Metric.WEIGHT -> byWeight[d]?.weightKg
                    Metric.SLEEP -> byDate[d]?.sleepHours?.takeIf { it > 0f }
                    Metric.WATER -> byDate[d]?.waterGlasses?.takeIf { it > 0 }?.toFloat()
                    else -> byDate[d]?.let { m.value(it).toFloat() }
                }

                // A metric is offered only when it has at least one real point in
                // this range, so a missing indicator never breaks the chart.
                val availableMetrics = MetricOptions.filter { m ->
                    days.any { d -> rawFor(m, d) != null }
                }

                if (availableMetrics.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "هر شاخص را می‌توانی روشن یا خاموش کنی؛ فقط داده‌های واقعی ثبت‌شده نمایش داده می‌شوند.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    MultiChoiceChips(
                        options = availableMetrics.map { it.label },
                        selected = availableMetrics.filter { it in selectedMetrics }.map { it.label },
                        accent = Purple
                    ) { pickedLabels ->
                        val picked = availableMetrics.filter { it.label in pickedLabels }
                        selectedMetrics = picked.toSet()
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (availableMetrics.isEmpty()) {
                    Text(
                        "برای دیدن نمودار، چند روز وضعیتت را ثبت کن. " +
                                "فقط داده‌های واقعی تو نمایش داده می‌شود.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val visible = availableMetrics.filter { it in selectedMetrics }
                        .ifEmpty { listOf(availableMetrics.first()) }
                    val series = visible.map { m ->
                        val pts = days.map { d -> rawFor(m, d) }
                        val avg = pts.filterNotNull().average().toFloat()
                        val scale = when (m) {
                            // Weight centres on its own average so small changes stay visible.
                            Metric.WEIGHT -> (avg + 20f).coerceAtLeast(1f)
                            else -> m.scale
                        }
                        ChartSeries(
                            label = m.label,
                            color = m.color,
                            points = pts,
                            scale = scale,
                            unit = when (m) {
                                Metric.SLEEP -> "ساعت"
                                Metric.WATER -> "لیوان"
                                Metric.WEIGHT -> "کیلو"
                                else -> "٪"
                            }
                        )
                    }
                    MultiLineChart(
                        values = days.map { Dates.shortLabel(it) },
                        series = series
                    )
                    Spacer(Modifier.height(10.dp))
                    visible.forEach { m ->
                        val real = days.mapNotNull { d -> rawFor(m, d) }
                        val avg = if (real.isEmpty()) null else real.average()
                        Row(
                            Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(m.color)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(m.label, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                            Text(
                                if (avg == null) "ثبت نشده"
                                else "میانگین ${m.format(avg)}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
 * The full-width welcome card at the very top of the dashboard. It always uses
 * the name the user gave in onboarding and carries the settings shortcut. Date
 * and cycle day live in their own separated pills so numbers and words never
 * run into each other in RTL.
 */
@Composable
private fun GreetingCard(
    name: String,
    emoji: String,
    dateLabel: String,
    cycleDay: Int,
    onSettings: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Shape.card)
            .background(BrandBrush)
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-34).dp)
                .size(132.dp)
                .clip(RoundedCornerShape(66.dp))
                .background(Color.White.copy(alpha = .10f))
        )
        Column(Modifier.padding(horizontal = 18.dp, vertical = 17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(Shape.badge)
                        .background(Color.White.copy(alpha = .22f)),
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = 23.sp) }
                Spacer(Modifier.width(12.dp))
                Text(
                    greetingFor(name),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(Shape.badge)
                        .background(Color.White.copy(alpha = .22f))
                        .clickable { onSettings() },
                    contentAlignment = Alignment.Center
                ) { Text("⚙️", fontSize = 20.sp) }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeaderPill("📅", dateLabel)
                if (cycleDay > 0) {
                    HeaderPill("🩷", "روز ${Dates.fa(cycleDay)} چرخه")
                }
            }
        }
    }
}

/** A compact, self-contained value chip used inside the greeting card. */
@Composable
private fun HeaderPill(emoji: String, text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = .18f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 12.5.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Dashboard card for the condition the user declared. The user's own words are
 * always shown first under a clear label; the AI explanation below is framed as
 * education, never diagnosis. When the AI cannot be reached, the last good text
 * stays visible and a plain Persian error is shown instead of an empty card.
 */
@Composable
private fun ConditionCard(
    conditions: String,
    info: String?,
    error: String?,
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
                Text("وضعیت پزشکی تو", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "شرایط ثبت‌شده: ",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        conditions,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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

        when {
            info.isNullOrBlank() && error == null -> {
                Text(
                    "توضیح کوتاه و آموزشی این شرایط را می‌سازم…",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                info?.lines()?.filter { it.isNotBlank() }?.forEach { raw ->
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
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "⚠️ $error",
                        fontSize = 11.5.sp,
                        color = Amber
                    )
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

/** Cycle tile, deliberately laid out like the BMI tile next to it. */
@Composable
private fun CycleTile(
    cycleDay: Int,
    phase: String?,
    daysUntil: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .height(BodyTileHeight)
            .clip(Shape.card)
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.Top
    ) {
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

        if (cycleDay > 0) {
            Text(
                "روز ${Dates.fa(cycleDay)}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (phase != null) {
                Spacer(Modifier.height(3.dp))
                Text(phase, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Pink)
            }
            Spacer(Modifier.height(6.dp))
            if (daysUntil != null && daysUntil > 0) {
                BmiRow("⏳", "پریود بعدی", "حدود ${Dates.fa(daysUntil)} روز دیگر")
            } else {
                BmiRow("🌸", "وضعیت", "امروز در بازه قاعدگی")
            }
        } else {
            Text("ثبت نشده", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(
                "برای محاسبه، تاریخ آخرین پریود را در پروفایل ثبت کن",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Fixed height shared by the BMI and cycle tiles so they stay the same size. */
private val BodyTileHeight = 148.dp

/**
 * Full-width smart tip derived from the user's current cycle phase. It always
 * uses real cycle data; when AI is available the text is personalised, and the
 * local phase tip is shown instantly while that happens.
 */
@Composable
private fun CycleTipCard(
    localTip: String?,
    aiTip: String?,
    error: String?,
    loading: Boolean,
    hasData: Boolean,
    onRefresh: () -> Unit
) {
    val shown = aiTip ?: localTip
    DastyarCard(accent = Pink) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Pink.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Text("🌸", fontSize = 19.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("پیشنهاد امروز برای چرخه تو 🌸", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "بر اساس روز چرخه و اطلاعات خودت",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (hasData) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "تولید دوباره")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (!hasData || shown == null) {
            Text(
                "اطلاعات کافی برای محاسبه دقیق وجود ندارد. " +
                        "تاریخ آخرین پریود، طول چرخه و مدت پریود را در پروفایل ثبت کن.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(shown, fontSize = 13.sp, lineHeight = 21.sp)
            if (loading) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "در حال شخصی‌سازی بیشتر…",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text("⚠️ $error", fontSize = 11.sp, color = Pink)
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
    error: String?,
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
            } else if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text("⚠️ $error", fontSize = 11.sp, color = Green)
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
                "${Dates.fa(water)} از ${Dates.fa(goal)}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
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

    Column(
        modifier
            .height(BodyTileHeight)
            .clip(Shape.card)
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onUpdate() }
            .padding(14.dp),
        verticalArrangement = Arrangement.Top
    ) {
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
                    "شاخص توده بدنی ${Dates.fa("%.1f".format(bmi))}",
                    fontSize = 13.sp,
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
                    Spacer(Modifier.height(6.dp))
                    BmiRow("⚖️", "وزن", "${Dates.fa("%.1f".format(it.weightKg))} کیلوگرم")
                    Spacer(Modifier.height(3.dp))
                    BmiRow("📅", "آخرین ثبت", Dates.pretty(it.date))
                }
            }
        }
    }
}

/** One label/value line inside the BMI tile, safely ordered for RTL. */
@Composable
private fun BmiRow(emoji: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 10.5.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    var text by remember {
        mutableStateOf(Dates.displayField(if (current > 0f) current.toString() else ""))
    }
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
                    onValueChange = { v -> text = Dates.displayField(Dates.decimalInput(v, 6)) },
                    label = { Text("وزن (کیلوگرم)") },
                    placeholder = { Text("مثلاً ۶۲") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { Dates.parseNum(text)?.let(onSave) }) { Text("ذخیره") }
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

private fun skinHint(ci: CheckIn?): String = when {
    ci == null || ci.skinStatus.isBlank() -> "برای دیدن راهنمای پوست، امروز ثبت کن"
    ci.skinStatus.contains("بهتر") -> "روتین فعلی‌ات دارد جواب می‌دهد"
    ci.skinStatus.contains("بدتر") -> "روتین را ساده‌تر کن و مرطوب‌کننده بزن"
    else -> "روتین ساده و ضدآفتاب را ادامه بده"
}

private fun fatigueHint(ci: CheckIn?): String = when {
    ci == null || ci.fatigueSeverity.isBlank() -> "برای پیگیری انرژی، امروز ثبت کن"
    ci.fatigueSeverity.contains("خیلی زیاد") || ci.fatigueSeverity.contains("زیاد") ->
        "امروز بار خودت را کم کن و بیشتر استراحت کن"
    ci.fatigueSeverity.contains("متوسط") -> "یک استراحت کوتاه در میانه روز کمک می‌کند"
    else -> "انرژی‌ات خوب است؛ همین ریتم را نگه دار"
}

/** Fatigue label from the questionnaire baseline when today has no check-in. */
private fun profileHintFatigue(p: Profile?): String =
    p?.fatigueLevel?.takeIf { it.isNotBlank() } ?: "ثبت نشده"

/**
 * The skin card. It shows the user's real skin state — today's check-in when
 * present, otherwise the questionnaire baseline — and only says nothing was
 * recorded when there genuinely is no skin information at all. An AI suggestion
 * for today sits below it.
 */
@Composable
private fun SkinCard(
    summary: String?,
    tip: String?,
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
            ) { Text("✨", fontSize = 19.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("وضعیت پوست", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    if (summary != null) "از اطلاعات ثبت‌شده خودت" else "اطلاعاتی ثبت نشده",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (summary != null) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "تولید دوباره")
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (summary == null) {
            Text(
                "هنوز اطلاعات پوستی ثبت نشده. با ثبت وضعیت امروز، این کارت کامل می‌شود.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text("وضعیت پوست: $summary", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            if (tip != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "پیشنهاد امروز:",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Amber
                )
                Spacer(Modifier.height(3.dp))
                Text(tip, fontSize = 13.sp, lineHeight = 21.sp)
            } else if (loading) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "در حال آماده‌سازی پیشنهاد…",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One togglable indicator in the unified trend chart. Every value comes from a
 * real recorded check-in (weight from the weight log); a day without data is
 * left as a gap, never filled in.
 */
private enum class Metric(val label: String, val color: Color, val scale: Float) {
    ENERGY("انرژی", Purple, 100f),
    SLEEP("خواب", Cyan, 12f),
    WATER("آب", Color(0xFF38BDF8), 15f),
    FATIGUE("بی‌رمقی", Rose, 100f),
    SKIN("پوست", Amber, 100f),
    WEIGHT("وزن", Green, 120f);

    fun value(ci: CheckIn): Int = when (this) {
        ENERGY -> energyPercent(ci)
        SLEEP -> (ci.sleepHours * 10f).toInt()
        WATER -> ci.waterGlasses
        FATIGUE -> fatigueScore(ci)
        SKIN -> skinScore(ci)
        WEIGHT -> 0
    }

    /** Average of the raw (unscaled) values, formatted in Persian. */
    fun format(avg: Double): String = when (this) {
        SLEEP -> "${Dates.fa("%.1f".format(avg / 10f))} ساعت"
        WATER -> "${Dates.fa("%.0f".format(avg))} لیوان"
        else -> "${Dates.fa("%.0f".format(avg))}٪"
    }
}

private val MetricOptions = Metric.entries

/**
 * A named, coloured series for the multi-line chart. [points] holds the raw
 * values (null = no data that day); the chart scales them to 0-100 on a shared
 * axis, and touch shows the raw value with [unit].
 */
data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Float?>,
    val scale: Float,
    val unit: String
)

/**
 * The unified status trend: several indicators on one chart, each with its own
 * colour, on a shared 0-100 axis. Missing days stay as gaps so nothing is
 * invented. Tapping the chart shows the values of all visible series for the
 * nearest day. The legend lives above the chart so it stays tidy on a phone.
 */
@Composable
fun MultiLineChart(values: List<String>, series: List<ChartSeries>) {
    val n = values.size.coerceAtLeast(2)
    var selected by remember { mutableStateOf(-1) }

    Column {
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(values, series) {
                    detectTapGestures { offset ->
                        val pad = 14f
                        val w = size.width
                        val step = (w - 2 * pad) / (n - 1).coerceAtLeast(1)
                        val idx = ((offset.x - pad) / step).roundToInt().coerceIn(0, n - 1)
                        selected = if (selected == idx) -1 else idx
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val pad = 14f

            fun px(i: Int) = pad + (w - 2 * pad) * (i.toFloat() / (n - 1))
            fun py(value: Float, scale: Float) =
                h - pad - (h - 2 * pad) * ((value / scale) * 100f / 100f).coerceIn(0f, 1f)

            repeat(4) { g ->
                val y = pad + (h - 2 * pad) * (g / 3f)
                drawLine(Color.Gray.copy(alpha = .18f), Offset(pad, y), Offset(w - pad, y), 1f)
            }

            // Highlight the tapped day across all series.
            if (selected in 0 until n) {
                drawLine(
                    Purple.copy(alpha = .45f),
                    Offset(px(selected), pad),
                    Offset(px(selected), h - pad),
                    2f
                )
            }

            series.forEach { s ->
                val path = Path()
                var started = false
                s.points.forEachIndexed { i, v ->
                    if (v == null) {
                        started = false
                    } else {
                        val x = px(i); val y = py(v, s.scale)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                    }
                }
                drawPath(path, s.color, style = Stroke(width = 2.5f))
                s.points.forEachIndexed { i, v ->
                    if (v != null) {
                        val big = i == selected
                        drawCircle(s.color, radius = if (big) 6.5f else 4.5f,
                            center = Offset(px(i), py(v, s.scale)))
                        drawCircle(Color.White, radius = if (big) 2.6f else 1.8f,
                            center = Offset(px(i), py(v, s.scale)))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val step = (values.size / 7).coerceAtLeast(1)
            values.filterIndexed { i, _ -> i % step == 0 }.forEach {
                Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (selected in 0 until n) {
            val label = values.getOrNull(selected).orEmpty()
            val shown = series.mapNotNull { s ->
                s.points.getOrNull(selected)?.let { v -> "${s.label}: ${Dates.fa("%.1f".format(v))} ${s.unit}" }
            }
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "روز $label — " + shown.joinToString(" • "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
