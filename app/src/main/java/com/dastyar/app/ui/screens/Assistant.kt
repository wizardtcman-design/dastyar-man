package com.dastyar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.ChatMessage
import com.dastyar.app.data.CheckIn
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Health
import com.dastyar.app.data.Profile
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Green
import com.dastyar.app.ui.theme.Pink
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.ui.theme.Rose

/**
 * The three specialist advisors. Each has its own identity colour, icon and
 * short title so the home cards and the advisor screens stay consistent.
 */
private enum class Advisor(
    val key: String,
    val emoji: String,
    val title: String,
    val short: String,
    val accent: Color
) {
    PERIOD("period", "🩸", "پریود و چرخه", "پریود و چرخه", Pink),
    SKIN("skin", "✨", "مراقبت پوست", "مراقبت پوست", Amber),
    ENERGY("fatigue", "⚡", "انرژی و بی‌رمقی", "انرژی و بی‌رمقی", Rose)
}

@Composable
fun AssistantScreen(vm: MainViewModel) {
    var open by remember { mutableStateOf<Advisor?>(null) }

    val target = open
    if (target == null) {
        AssistantHome(vm = vm, onOpen = { open = it })
    } else {
        AdvisorScreen(vm = vm, advisor = target, onBack = { open = null })
    }
}

// ------------------------------------------------------------------ home

@Composable
private fun AssistantHome(vm: MainViewModel, onOpen: (Advisor) -> Unit) {
    val profile by vm.profile.collectAsState()
    val today by vm.todayCheckIn.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        ScreenHeader(
            emoji = "🤖",
            title = "دستیار من",
            subtitle = "یک موضوع را انتخاب کن و از مشاورهٔ تخصصی همان بخش استفاده کن."
        )
        Spacer(Modifier.height(16.dp))

        // Three equal cards, one per advisor, each with real current status.
        Advisor.entries.forEach { a ->
            AdvisorCard(
                advisor = a,
                status = advisorStatus(a, profile, today),
                onClick = { onOpen(a) }
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(4.dp))
        DastyarCard {
            Text(
                "دستیار من تشخیص پزشکی نمی‌دهد و جایگزین پزشک نیست. " +
                        "در صورت وجود علائم هشدار، لطفاً به پزشک مراجعه کن.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * One advisor card. All three share the exact same size, spacing and
 * typography; only the accent colour and content change.
 */
@Composable
private fun AdvisorCard(advisor: Advisor, status: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, advisor.accent.copy(alpha = .35f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(advisor.accent.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Text(advisor.emoji, fontSize = 24.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(advisor.title, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    status,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text("‹", fontSize = 20.sp, color = advisor.accent)
        }
    }
}

/** A one-line, real status for a card. Never a fixed placeholder. */
private fun advisorStatus(a: Advisor, p: Profile?, today: CheckIn?): String = when (a) {
    Advisor.PERIOD -> {
        val day = Health.cycleDay(p)
        val phase = Health.phaseLabel(p)
        when {
            day > 0 && phase != null -> "روز ${Dates.fa(day)} چرخه • $phase"
            else -> "برای محاسبه، تاریخ آخرین پریودت را ثبت کن"
        }
    }
    Advisor.SKIN -> {
        val s = Health.skinSummary(p, today)
        when {
            !s.isNullOrBlank() -> "وضعیت پوست: $s"
            else -> "برای شروع، وضعیت پوستت را ثبت کن"
        }
    }
    Advisor.ENERGY -> {
        val e = today?.energyLevel?.takeIf { it.isNotBlank() }
        when {
            e != null -> "انرژی امروز: $e"
            else -> "برای شروع، انرژی امروزت را ثبت کن"
        }
    }
}

// --------------------------------------------------------- advisor screen

@Composable
private fun AdvisorScreen(vm: MainViewModel, advisor: Advisor, onBack: () -> Unit) {
    val profile by vm.profile.collectAsState()
    val today by vm.todayCheckIn.collectAsState()
    val cycleTip by vm.cycleTip.collectAsState()
    val skinTip by vm.skinTip.collectAsState()

    // Load the personalised AI tip for the topic once, when data is available.
    LaunchedEffect(advisor, profile, today) {
        when (advisor) {
            Advisor.PERIOD -> vm.loadCycleTip()
            Advisor.SKIN -> vm.loadSkinTip()
            Advisor.ENERGY -> Unit
        }
    }

    val rows = summaryRows(advisor, profile, today)
    val aiTip = when (advisor) {
        Advisor.PERIOD -> cycleTip
        Advisor.SKIN -> skinTip
        Advisor.ENERGY -> null
    }
    val localTip = localTip(advisor, profile, today)

    Column(Modifier.fillMaxSize()) {
        AdvisorHeader(advisor, onBack)
        AdvisorBody(
            advisor = advisor,
            rows = rows,
            aiTip = aiTip,
            localTip = localTip,
            hasData = rows.isNotEmpty(),
            onCheckIn = { vm.requestOpenCheckIn() }
        )
        ChatDock(
            vm = vm,
            advisor = advisor,
            profile = profile,
            today = today
        )
    }
}

@Composable
private fun AdvisorHeader(advisor: Advisor, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(advisor.accent.copy(alpha = .28f), Purple.copy(alpha = .20f))
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "بازگشت")
        }
        Text(
            "دستیار ${advisor.title}",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f)
        )
        Text(advisor.emoji, fontSize = 20.sp)
    }
}

@Composable
private fun ColumnScope.AdvisorBody(
    advisor: Advisor,
    rows: List<Pair<String, String>>,
    aiTip: String?,
    localTip: String?,
    hasData: Boolean,
    onCheckIn: () -> Unit
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (!hasData) {
            EmptyState(advisor, onCheckIn)
        } else {
            SectionTitle("خلاصهٔ وضعیت امروز", advisor.emoji)
            Spacer(Modifier.height(8.dp))
            DastyarCard(accent = advisor.accent) {
                rows.forEachIndexed { i, (label, value) ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            label,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            value,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionTitle("پیشنهاد هوشمند امروز", "💡")
            Spacer(Modifier.height(8.dp))
            DastyarCard(accent = Purple) {
                val shown = aiTip?.takeIf { it.isNotBlank() } ?: localTip
                if (shown.isNullOrBlank()) {
                    Text(
                        "در حال آماده‌سازی پیشنهاد بر اساس اطلاعات امروزت…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(shown, fontSize = 13.5.sp, lineHeight = 22.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "این پیشنهادها عمومی‌اند و جای نظر پزشک را نمی‌گیرند.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun EmptyState(advisor: Advisor, onCheckIn: () -> Unit) {
    DastyarCard(accent = advisor.accent) {
        Text(advisor.emoji, fontSize = 30.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "برای شخصی‌سازی بهتر، چند اطلاعات کوتاه از وضعیت امروزت لازم دارم.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onCheckIn,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ثبت اطلاعات امروز") }
    }
}

// ------------------------------------------------------------- summaries

/** Real summary rows for the topic; empty when there is nothing recorded. */
private fun summaryRows(a: Advisor, p: Profile?, today: CheckIn?): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    when (a) {
        Advisor.PERIOD -> {
            val day = Health.cycleDay(p)
            if (day > 0) rows += "روز چرخه" to Dates.fa(day)
            Health.phaseLabel(p)?.let { rows += "مرحلهٔ فعلی" to it }
            Health.nextPeriodDate(p)?.let { d ->
                rows += "پریود بعدی" to Dates.fa(d)
            }
            today?.takeIf { it.periodPain.isNotBlank() }?.let { ci ->
                rows += "درد" to ci.periodPain
            }
            today?.takeIf { it.periodPainLocation.isNotBlank() }?.let {
                rows += "محل درد" to it.periodPainLocation
            }
            today?.takeIf { it.isPeriodDay }?.let { rows += "امروز" to "روز پریود" }
            if (rows.isEmpty()) {
                if (p?.painLocation?.isNotBlank() == true) rows += "الگوی درد" to p.painLocation
                if (p?.bleedingLevel?.isNotBlank() == true) rows += "شدت خونریزی" to p.bleedingLevel
            }
        }
        Advisor.SKIN -> {
            today?.takeIf { it.skinStatus.isNotBlank() }?.let { rows += "وضعیت امروز" to it.skinStatus }
            today?.takeIf { it.acneCount.isNotBlank() }?.let { rows += "جوش‌ها" to it.acneCount }
            today?.takeIf { it.skinInflammation.isNotBlank() }?.let { rows += "التهاب" to it.skinInflammation }
            today?.takeIf { it.skinDryOily.isNotBlank() }?.let { rows += "خشکی/چربی" to it.skinDryOily }
            if (rows.isEmpty()) {
                if (p?.skinType?.isNotBlank() == true) rows += "نوع پوست" to p.skinType
                if (p?.acneLevel?.isNotBlank() == true) rows += "جوش" to p.acneLevel
                if (p?.acneLocation?.isNotBlank() == true) rows += "محل جوش" to p.acneLocation
            }
        }
        Advisor.ENERGY -> {
            today?.takeIf { it.energyLevel.isNotBlank() }?.let { rows += "انرژی امروز" to it.energyLevel }
            today?.takeIf { it.fatigueSeverity.isNotBlank() }?.let { rows += "بی‌رمقی" to it.fatigueSeverity }
            today?.takeIf { it.sleepQuality.isNotBlank() }?.let { rows += "کیفیت خواب" to it.sleepQuality }
            today?.takeIf { it.sleepHours > 0f }?.let { rows += "مدت خواب" to "${Dates.fa(it.sleepHours)} ساعت" }
            today?.takeIf { it.waterGlasses > 0 }?.let { rows += "آب" to "${Dates.fa(it.waterGlasses)} لیوان" }
            today?.takeIf { it.stressLevel.isNotBlank() }?.let { rows += "استرس" to it.stressLevel }
            if (rows.isEmpty()) {
                if (p?.fatigueLevel?.isNotBlank() == true) rows += "سطح بی‌رمقی" to p.fatigueLevel
                if (p?.sleepHours != null && p.sleepHours > 0f) rows += "خواب معمول" to "${Dates.fa(p.sleepHours)} ساعت"
            }
        }
    }
    return rows
}

/** A short, local fallback tip shown instantly before/without the AI reply. */
private fun localTip(a: Advisor, p: Profile?, today: CheckIn?): String? = when (a) {
    Advisor.PERIOD -> Health.cycleTip(p, today)
    Advisor.SKIN -> Health.skinSummary(p, today)?.let {
        "بر اساس ثبت امروز ($it)، یک روتین ساده و ملایم را حفظ کن: شست‌وشوی آرام صبح و شب، " +
                "مرطوب‌کننده مناسب، و ضدآفتاب در طول روز."
    }
    Advisor.ENERGY -> when {
        today?.sleepHours != null && today.sleepHours in 0.1f..5.9f ->
            "دیشب کم خوابیدی؛ امروز آب کافی بنوش، کافئین را کم کن و یک استراحت کوتاه در میانهٔ روز بگذار."
        today?.energyLevel?.contains("کم") == true ->
            "انرژی امروزت پایین است؛ کارهای سنگین را به زمان دیگری بسپار و یک فعالیت سبک و هوای تازه را امتحان کن."
        else -> null
    }
}

// ------------------------------------------------------------- chat dock

/**
 * Shared chat dock used by all three advisors. A rounded, modern composer with
 * a message list above it, quick-question chips and a gradient send button.
 * The whole block sits at the bottom and rises with the keyboard because the
 * activity uses adjustResize.
 */
@Composable
private fun ChatDock(
    vm: MainViewModel,
    advisor: Advisor,
    profile: Profile?,
    today: CheckIn?
) {
    val messages by vm.chatFlow(advisor.key).collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Reads `messages.size` so the busy flag clears when a reply arrives.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        if (busy) busy = false
    }

    val suggestions = remember(advisor, profile, today, messages.size) {
        dynamicSuggestions(advisor, profile, today) + staticSuggestions(advisor)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                advisor.accent.copy(alpha = .20f),
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            )
            .imePadding()
    ) {
        // message list, compact
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp, max = 190.dp)
                .padding(horizontal = 10.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "سلام! هر سؤالی داری بپرس — درباره وضعیت خودت هم می‌دانم.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            items(messages) { m -> MessageBubble(m, advisor.accent) }
            if (busy) {
                item {
                    Text(
                        "دستیار در حال نوشتن…",
                        fontSize = 12.sp,
                        color = advisor.accent,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }

        // quick-question chips, horizontally scrollable so they never crowd
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { s ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = advisor.accent.copy(alpha = .12f),
                    modifier = Modifier.clickable(enabled = !busy) {
                        if (!busy) { busy = true; vm.chat(advisor.key, s) }
                    }
                ) {
                    Text(
                        s,
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // composer
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("پیامت را بنویس…", fontSize = 13.sp) },
                shape = RoundedCornerShape(20.dp),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (input.isNotBlank() && !busy)
                            Brush.linearGradient(listOf(Purple, advisor.accent))
                        else
                            Brush.linearGradient(
                                listOf(
                                    Color.Gray.copy(alpha = .35f),
                                    Color.Gray.copy(alpha = .35f)
                                )
                            )
                    )
                    .clickable(enabled = input.isNotBlank() && !busy) {
                        val text = input.trim()
                        if (text.isNotEmpty() && !busy) {
                            input = ""
                            busy = true
                            vm.chat(advisor.key, text)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Send, contentDescription = "ارسال", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMessage, accent: Color) {
    val isUser = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End
    ) {
        Box(
            Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(18.dp))
                .then(
                    if (isUser) Modifier
                        .background(accent.copy(alpha = .14f))
                        .border(1.dp, accent.copy(alpha = .35f), RoundedCornerShape(18.dp))
                    else Modifier.background(
                        Brush.linearGradient(
                            listOf(Purple.copy(alpha = .88f), accent.copy(alpha = .78f))
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(
                m.content,
                fontSize = 13.5.sp,
                color = if (isUser) MaterialTheme.colorScheme.onSurface else Color.White
            )
        }
    }
}

// ------------------------------------------------------ quick questions

/** Fixed, topic-scoped questions the user can tap to send. */
private fun staticSuggestions(a: Advisor): List<String> = when (a) {
    Advisor.PERIOD -> listOf(
        "امروز در چه مرحله‌ای از چرخه هستم؟",
        "برای امروز چه مراقبتی پیشنهاد می‌کنی؟",
        "برای درد امروز چه کارهایی می‌توانم انجام دهم؟",
        "برای این مرحله از چرخه چه غذایی مناسب است؟"
    )
    Advisor.SKIN -> listOf(
        "امروز برای پوستم چه کار کنم؟",
        "روتین امشب پوستم را بگو",
        "چه چیزهایی ممکن است جوش‌هایم را بدتر کنند؟",
        "یک مراقبت خانگی کم‌خطر پیشنهاد بده"
    )
    Advisor.ENERGY -> listOf(
        "امروز برای انرژی بیشتر چه کار کنم؟",
        "با توجه به خوابم وضعیت انرژی‌ام چطور است؟",
        "امروز چقدر آب بخورم؟",
        "برای بی‌رمقی امروز چه پیشنهادی داری؟"
    )
}

/**
 * Data-driven questions shown before the fixed ones, only when the user's real
 * state actually warrants them. Nothing is added on a guess.
 */
private fun dynamicSuggestions(a: Advisor, p: Profile?, today: CheckIn?): List<String> {
    val out = mutableListOf<String>()
    when (a) {
        Advisor.PERIOD -> {
            when (Health.phase(p)) {
                Health.CyclePhase.OVULATION ->
                    out += "امروز در مرحله تخمک‌گذاری هستی؛ مراقبت‌های مناسب این مرحله را ببینم؟"
                Health.CyclePhase.LUTEAL ->
                    out += "در مرحله لوتئال هستم؛ چطور نوسان خلق و انرژی را مدیریت کنم؟"
                Health.CyclePhase.MENSTRUAL ->
                    out += "دوران قاعدگی‌ام است؛ چه چیزهایی در این روزها کمکم می‌کند؟"
                else -> Unit
            }
            if (today?.periodPain?.isNotBlank() == true || today?.periodPainLevel?.isNotBlank() == true) {
                out += "امروز درد ثبت کردم؛ چه کارهایی می‌تواند آرامم کند؟"
            }
        }
        Advisor.SKIN -> {
            when (today?.skinStatus) {
                "بدتر شده" -> out += "به نظر می‌رسد وضعیت پوستم بدتر شده؛ می‌خواهی با هم بررسی کنیم؟"
                "بهتر شده" -> out += "پوستم بهتر شده؛ چطور این روند را حفظ کنم؟"
                else -> Unit
            }
            if (today?.skinNewProduct?.isNotBlank() == true) {
                out += "محصول جدیدی استفاده کردم؛ چطور بفهمم برای پوستم مناسب است؟"
            }
        }
        Advisor.ENERGY -> {
            if (today != null && today.sleepHours > 0f && today.sleepHours < 6f) {
                out += "دیشب خوابم کم بوده؛ می‌خواهی ببینیم امروز چطور انرژی‌ام را مدیریت کنم؟"
            }
            if (today?.fatigueSeverity?.isNotBlank() == true) {
                out += "امروز بی‌رمقی ثبت کردم؛ چه چیزی می‌تواند کمکم کند؟"
            }
            if (today != null && today.waterGlasses < 4) {
                out += "امروز کم آب خورده‌ام؛ چقدر باید بنوشم؟"
            }
        }
    }
    return out
}
