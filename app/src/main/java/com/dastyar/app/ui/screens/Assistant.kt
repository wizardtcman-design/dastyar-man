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
import androidx.compose.ui.text.style.TextAlign
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
import com.dastyar.app.ui.theme.Pink
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.ui.theme.Rose

/**
 * The three specialist advisors. Each has its own identity colour, icon and
 * title so the home cards and the advisor screens stay consistent.
 * `key` also selects the chat channel stored in the database.
 */
private enum class Advisor(
    val key: String,
    val emoji: String,
    val title: String,
    val accent: Color
) {
    PERIOD("period", "🩸", "مشاوره پریود", Pink),
    SKIN("skin", "✨", "مشاوره پوست", Amber),
    ENERGY("fatigue", "⚡", "مشاوره بی‌رمقی", Rose)
}

/** The general assistant chat, kept separate from the three advisors. */
private const val GENERAL_CHANNEL = "general"

@Composable
fun AssistantScreen(vm: MainViewModel) {
    var open by remember { mutableStateOf<Advisor?>(null) }

    // One real keyboard adaptation for the whole section. Edge-to-edge is on and
    // the IME inset is not consumed anywhere else, so `imePadding` here lifts the
    // entire assistant UI above the keyboard. Inside, the chat panel is a flex
    // Column whose message list takes `weight(1f)`: the list shrinks exactly by
    // the keyboard height while the composer stays pinned on top of it. No
    // spacer or fake padding is involved, so it also works on a small screen.
    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        val target = open
        if (target == null) {
            AssistantHome(vm = vm, onOpen = { open = it })
        } else {
            AdvisorScreen(vm = vm, advisor = target, onBack = { open = null })
        }
    }
}

// ------------------------------------------------------------------ home

/**
 * Home: a fixed, non-scrolling header block of three equal cards, then a full
 * general-assistant chat that fills the rest of the screen. The chat's composer
 * stays pinned at the bottom and rises with the keyboard, because the whole
 * screen is a Column whose middle row takes `weight(1f)`.
 */
@Composable
private fun AssistantHome(vm: MainViewModel, onOpen: (Advisor) -> Unit) {
    val profile by vm.profile.collectAsState()
    val today by vm.todayCheckIn.collectAsState()

    Column(Modifier.fillMaxSize()) {
        // ---- fixed header: three identical cards, titles only ----
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp)
        ) {
            ScreenHeader(
                emoji = "🤖",
                title = "دستیار من",
                subtitle = "یک موضوع را انتخاب کن یا هر سؤالی داری همین‌جا بپرس."
            )
            Spacer(Modifier.height(12.dp))
            Advisor.entries.forEach { a ->
                AdvisorCard(advisor = a, onClick = { onOpen(a) })
                Spacer(Modifier.height(10.dp))
            }
        }

        // ---- general chat fills the remaining space ----
        ChatPanel(
            vm = vm,
            channel = GENERAL_CHANNEL,
            accent = Purple,
            emptyHint = "سلام! من دستیار شخصی تو هستم. هر سؤالی داری بپرس — " +
                    "برنامه روزانه، ایده، اطلاعات، آشپزی یا هر چیز دیگر.",
            suggestions = generalSuggestions(),
            profile = profile,
            today = today,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * One advisor card. All three are exactly the same size and only show the
 * title, so long or missing data can never change the layout. The title is
 * allowed to wrap onto two lines inside the fixed height.
 */
@Composable
private fun AdvisorCard(advisor: Advisor, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
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
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(advisor.accent.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Text(advisor.emoji, fontSize = 22.sp) }
            Spacer(Modifier.width(14.dp))
            Text(
                advisor.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Text("‹", fontSize = 20.sp, color = advisor.accent)
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

    // Column: fixed header + scrollable content (weight 1) + fixed chat panel.
    Column(Modifier.fillMaxSize()) {
        AdvisorHeader(advisor, onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (rows.isEmpty()) {
                EmptyState(advisor) { vm.requestOpenCheckIn() }
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
                            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
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
                Spacer(Modifier.height(8.dp))
                Text(
                    "این پیشنهادها عمومی‌اند و جای نظر پزشک را نمی‌گیرند.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ChatPanel(
            vm = vm,
            channel = advisor.key,
            accent = advisor.accent,
            emptyHint = "سلام! من دستیار «${advisor.title}» هستم. " +
                    "درباره وضعیت خودت بپرس؛ اطلاعات ثبت‌شده‌ات را هم در نظر می‌گیرم.",
            suggestions = advisorSuggestions(advisor).plus(dynamicSuggestions(advisor, profile, today)),
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

// --------------------------------------------------------------- chat panel

/**
 * Shared, keyboard-aware chat used in all four places: the general assistant
 * and the three advisors.
 *
 * Layout contract:
 *  - the panel is a plain Column that fills the space it is given (the caller
 *    gives it `weight(1f)`, or the parent Box fills the screen);
 *  - the message list takes `weight(1f)`, so it shrinks by exactly the keyboard
 *    height and grows back when the keyboard closes, while staying scrollable;
 *  - the chips row and the composer are last, so they always sit directly above
 *    the keyboard and are never hidden under it.
 *
 * The IME inset is consumed once, by `AssistantScreen`, which wraps the whole
 * section in `imePadding`. Because this panel only re-flows its `weight(1f)`
 * child, no spacer or fake padding is involved and the composer tracks the
 * keyboard on every phone and keyboard height.
 */
@Composable
private fun ChatPanel(
    vm: MainViewModel,
    channel: String,
    accent: Color,
    emptyHint: String,
    suggestions: List<String>,
    profile: Profile?,
    today: CheckIn?,
    modifier: Modifier = Modifier
) {
    val messages by vm.chatFlow(channel).collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Reads messages.size so it re-runs when a reply arrives: scroll to the
    // newest message and clear the busy flag.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        if (busy) busy = false
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                accent.copy(alpha = .20f),
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            )
    ) {
        // messages — takes all remaining height and scrolls
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        emptyHint,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            items(messages) { m -> MessageBubble(m, accent) }
            if (busy) {
                item {
                    Text(
                        "دستیار در حال نوشتن…",
                        fontSize = 12.sp,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }

        // quick-question chips, horizontally scrollable so they never crowd
        if (suggestions.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { s ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accent.copy(alpha = .12f),
                        modifier = Modifier.clickable(enabled = !busy) {
                            if (!busy) { busy = true; vm.chat(channel, s) }
                        }
                    ) {
                        Text(
                            s,
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // composer — always the last row, pinned above the keyboard
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
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
                            Brush.linearGradient(listOf(Purple, accent))
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
                            vm.chat(channel, text)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Send, contentDescription = "ارسال", tint = Color.White)
            }
        }
    }
}

/** A user or assistant message bubble. RTL flows from the right by nature. */
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
                textAlign = TextAlign.Right,
                color = if (isUser) MaterialTheme.colorScheme.onSurface else Color.White
            )
        }
    }
}

// ------------------------------------------------------ quick questions

/** Suggestions for the general assistant — any topic. */
private fun generalSuggestions(): List<String> = listOf(
    "یه برنامه روزانه سبک برای امروز بده",
    "یه ایده برای شام امشب بده",
    "چطور امروز رو منظم‌تر پیش ببرم؟",
    "یه نکته انگیزشی برای امروز بگو"
)

/** Fixed, topic-scoped questions for each advisor. */
private fun advisorSuggestions(a: Advisor): List<String> = when (a) {
    Advisor.PERIOD -> listOf(
        "امروز در چه مرحله‌ای از چرخه هستم؟",
        "برای امروز چه مراقبتی پیشنهاد می‌کنی؟",
        "برای درد امروز چه کارهایی می‌توانم انجام دهم؟",
        "وضعیت چرخه‌ام را برایم توضیح بده."
    )
    Advisor.SKIN -> listOf(
        "امروز برای پوستم چه کار کنم؟",
        "روتین امروز پوستم چیست؟",
        "چه چیزهایی ممکن است جوش‌هایم را بدتر کنند؟",
        "یک راهکار خانگی کم‌خطر پیشنهاد بده."
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
                    out += "امروز در مرحله تخمک‌گذاری هستم؛ مراقبت‌های این مرحله را بگو"
                Health.CyclePhase.LUTEAL ->
                    out += "در مرحله لوتئال هستم؛ چطور نوسان خلق و انرژی را مدیریت کنم؟"
                Health.CyclePhase.MENSTRUAL ->
                    out += "دوران قاعدگی‌ام است؛ چه چیزهایی کمکم می‌کند؟"
                else -> Unit
            }
            if (today?.periodPain?.isNotBlank() == true || today?.periodPainLevel?.isNotBlank() == true) {
                out += "امروز درد ثبت کردم؛ چه کاری آرامم می‌کند؟"
            }
        }
        Advisor.SKIN -> {
            when (today?.skinStatus) {
                "بدتر شده" -> out += "وضعیت پوستم بدتر شده؛ با هم بررسی کنیم؟"
                "بهتر شده" -> out += "پوستم بهتر شده؛ چطور این روند را حفظ کنم؟"
                else -> Unit
            }
            if (today?.skinNewProduct?.isNotBlank() == true) {
                out += "محصول جدیدی استفاده کردم؛ برای پوستم مناسبه؟"
            }
        }
        Advisor.ENERGY -> {
            if (today != null && today.sleepHours > 0f && today.sleepHours < 6f) {
                out += "دیشب کم خوابیدم؛ امروز انرژی‌ام را چطور مدیریت کنم؟"
            }
            if (today?.fatigueSeverity?.isNotBlank() == true) {
                out += "امروز بی‌رمقی ثبت کردم؛ چه چیزی کمکم می‌کند؟"
            }
            if (today != null && today.waterGlasses < 4) {
                out += "امروز کم آب خورده‌ام؛ چقدر باید بنوشم؟"
            }
        }
    }
    return out
}

// ------------------------------------------------------------- summaries

/** Real summary rows for the topic; empty when there is nothing recorded. */
private fun summaryRows(a: Advisor, p: Profile?, today: CheckIn?): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    when (a) {
        Advisor.PERIOD -> {
            val day = Health.cycleDay(p)
            if (day > 0) rows += "روز چرخه" to Dates.fa(day)
            Health.phaseLabel(p)?.let { ph -> rows += "مرحلهٔ فعلی" to ph }
            Health.nextPeriodDate(p)?.let { d -> rows += "پریود بعدی" to Dates.fa(d) }
            today?.takeIf { it.periodPain.isNotBlank() }?.let { ci -> rows += "درد" to ci.periodPain }
            today?.takeIf { it.periodPainLocation.isNotBlank() }?.let { ci ->
                rows += "محل درد" to ci.periodPainLocation
            }
            if (today?.isPeriodDay == true) rows += "امروز" to "روز پریود"
            if (rows.isEmpty()) {
                if (p?.painLocation?.isNotBlank() == true) rows += "الگوی درد" to p.painLocation
                if (p?.bleedingLevel?.isNotBlank() == true) rows += "شدت خونریزی" to p.bleedingLevel
            }
        }
        Advisor.SKIN -> {
            today?.takeIf { it.skinStatus.isNotBlank() }?.let { ci -> rows += "وضعیت امروز" to ci.skinStatus }
            today?.takeIf { it.acneCount.isNotBlank() }?.let { ci -> rows += "جوش‌ها" to ci.acneCount }
            today?.takeIf { it.skinInflammation.isNotBlank() }?.let { ci ->
                rows += "التهاب" to ci.skinInflammation
            }
            today?.takeIf { it.skinDryOily.isNotBlank() }?.let { ci -> rows += "خشکی/چربی" to ci.skinDryOily }
            if (rows.isEmpty()) {
                if (p?.skinType?.isNotBlank() == true) rows += "نوع پوست" to p.skinType
                if (p?.acneLevel?.isNotBlank() == true) rows += "جوش" to p.acneLevel
                if (p?.acneLocation?.isNotBlank() == true) rows += "محل جوش" to p.acneLocation
            }
        }
        Advisor.ENERGY -> {
            today?.takeIf { it.energyLevel.isNotBlank() }?.let { ci -> rows += "انرژی امروز" to ci.energyLevel }
            today?.takeIf { it.fatigueSeverity.isNotBlank() }?.let { ci -> rows += "بی‌رمقی" to ci.fatigueSeverity }
            today?.takeIf { it.sleepQuality.isNotBlank() }?.let { ci -> rows += "کیفیت خواب" to ci.sleepQuality }
            today?.takeIf { it.sleepHours > 0f }?.let { ci ->
                rows += "مدت خواب" to "${Dates.fa(ci.sleepHours)} ساعت"
            }
            today?.takeIf { it.waterGlasses > 0 }?.let { ci ->
                rows += "آب" to "${Dates.fa(ci.waterGlasses)} لیوان"
            }
            today?.takeIf { it.stressLevel.isNotBlank() }?.let { ci -> rows += "استرس" to ci.stressLevel }
            if (rows.isEmpty()) {
                if (p?.fatigueLevel?.isNotBlank() == true) rows += "سطح بی‌رمقی" to p.fatigueLevel
                if (p != null && p.sleepHours > 0f) rows += "خواب معمول" to "${Dates.fa(p.sleepHours)} ساعت"
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
        today != null && today.sleepHours in 0.1f..5.9f ->
            "دیشب کم خوابیدی؛ امروز آب کافی بنوش، کافئین را کم کن و یک استراحت کوتاه در میانهٔ روز بگذار."
        today?.energyLevel?.contains("کم") == true ->
            "انرژی امروزت پایین است؛ کارهای سنگین را به زمان دیگری بسپار و یک فعالیت سبک و هوای تازه را امتحان کن."
        else -> null
    }
}
