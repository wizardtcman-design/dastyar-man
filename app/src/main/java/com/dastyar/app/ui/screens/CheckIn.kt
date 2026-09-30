package com.dastyar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.CheckIn
import com.dastyar.app.data.Dates
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Pink
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.ui.theme.Rose

/**
 * Daily check-in. The first day after onboarding asks a fuller set; later days
 * ask only what is relevant to the user's current situation, so it stays short.
 */
@Composable
fun CheckInScreen(vm: MainViewModel, onDone: () -> Unit) {
    val profile by vm.profile.collectAsState()
    val checkIns by vm.checkIns.collectAsState()
    val todayStr = Dates.today()

    var draft by remember { mutableStateOf<CheckIn?>(null) }
    LaunchedEffect(todayStr, checkIns) {
        if (draft == null) {
            draft = checkIns.firstOrNull { it.date == todayStr } ?: CheckIn(date = todayStr)
        }
    }

    val c = draft ?: return
    val previous = checkIns.firstOrNull { it.date != todayStr }
    val isFirstEver = previous == null
    val cycleDay = profile?.let {
        if (it.lastPeriodDate.isBlank()) 0
        else Dates.cycleDay(it.lastPeriodDate, it.cycleLength)
    } ?: 0
    val expectedPeriod = profile?.let {
        if (it.lastPeriodDate.isBlank()) false
        else Dates.isPeriodDay(it.lastPeriodDate, it.cycleLength, it.periodDays)
    } ?: false

    // Adaptive: on later days we only expand a section if the user says there
    // is something to report, or the cycle says a period is expected.
    var showSkinDetail by remember { mutableStateOf(isFirstEver) }
    var showFatigueDetail by remember { mutableStateOf(isFirstEver) }
    // The period questions stay open while a period is running: today's answer
    // was "yes", or an open period event exists (started but not yet ended).
    val periodRunning = vm.periodEvents.collectAsState().value.let { events ->
        val p = profile
        if (p == null || p.lastPeriodDate.isBlank()) false
        else events.firstOrNull { it.startIso == p.lastPeriodDate }?.endIso?.isBlank() == true
    }
    var showPeriodDetail by remember {
        mutableStateOf(isFirstEver || expectedPeriod || cycleDay in listOf(1, 2) || periodRunning)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        ScreenHeader(
            emoji = "🌤",
            title = if (isFirstEver) "وضعیت امروزت چطوره؟" else "چک‌این امروز",
            subtitle = Dates.pretty(todayStr) +
                    if (cycleDay > 0) " • روز ${Dates.fa(cycleDay)} چرخه" else ""
        )
        if (!isFirstEver) {
            Spacer(Modifier.height(8.dp))
            Text(
                "چند سؤال کوتاه؛ فقط چیزی که امروز مهمه.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))

        // ---------- always asked ----------
        SectionCard("💪", "حال کلی امروز", "انرژی، خواب، آب و استرس", Purple) {
            LabeledText("میزان انرژی امروز")
            ChoiceChips(
                listOf("خیلی خوب", "خوب", "متوسط", "کم", "خیلی کم"),
                c.energyLevel,
                accent = Purple
            ) { draft = c.copy(energyLevel = it) }

            Spacer(Modifier.height(18.dp))
            LabeledText("میزان خواب دیشب (ساعت)")
            OutlinedTextField(
                value = Dates.displayField(if (c.sleepHours == 0f) "" else c.sleepHours.toString()),
                onValueChange = { draft = c.copy(sleepHours = Dates.decimalInput(it, 4).toFloatOrNull() ?: 0f) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("مثلاً ۷.۵") }
            )

            Spacer(Modifier.height(18.dp))
            LabeledText("کیفیت خواب")
            ChoiceChips(listOf("عالی", "خوب", "متوسط", "ضعیف"), c.sleepQuality, accent = Cyan) {
                draft = c.copy(sleepQuality = it)
            }

            Spacer(Modifier.height(18.dp))
            LabeledText("آب مصرفی تا الان (لیوان)")
            ChoiceChips((0..12).map { Dates.fa(it) }, Dates.fa(c.waterGlasses), accent = Cyan) {
                draft = c.copy(waterGlasses = Dates.parseNum(it)?.toInt() ?: 0)
            }

            Spacer(Modifier.height(18.dp))
            LabeledText("سطح استرس امروز")
            ChoiceChips(listOf("کم", "متوسط", "زیاد", "خیلی زیاد"), c.stressLevel, accent = Amber) {
                draft = c.copy(stressLevel = it)
            }
        }

        Spacer(Modifier.height(14.dp))
        // ---------- skin summary + optional detail ----------
        SectionCard("✨", "پوست امروز", "وضعیت کلی و جزئیات", Amber) {
            LabeledText("وضعیت پوست نسبت به قبل")
            ChoiceChips(listOf("بهتر شده", "مثل قبل", "بدتر شده"), c.skinStatus, accent = Amber) {
                draft = c.copy(skinStatus = it)
                if (it == "بدتر شده") showSkinDetail = true
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showSkinDetail = !showSkinDetail }) {
                Text(if (showSkinDetail) "بستن جزئیات پوست" else "جزئیات بیشتر پوست ▾")
            }
            if (showSkinDetail) {
                Spacer(Modifier.height(6.dp))
                LabeledText("تعداد جوش‌ها")
                ChoiceChips(listOf("کمتر شده", "مثل قبل", "بیشتر شده"), c.acneCount, accent = Amber) {
                    draft = c.copy(acneCount = it)
                }
                Spacer(Modifier.height(16.dp))
                LabeledText("التهاب یا قرمزی")
                ChoiceChips(listOf("ندارم", "کم", "متوسط", "زیاد"), c.skinInflammation, accent = Amber) {
                    draft = c.copy(skinInflammation = it)
                }
                Spacer(Modifier.height(16.dp))
                LabeledText("خشکی یا چربی")
                ChoiceChips(listOf("خشکی", "چربی", "هیچ‌کدام", "هر دو"), c.skinDryOily, accent = Amber) {
                    draft = c.copy(skinDryOily = it)
                }
                Spacer(Modifier.height(16.dp))
                LabeledText("سوزش یا حساسیت")
                ChoiceChips(listOf("ندارم", "کم", "زیاد"), c.skinSensitivity, accent = Amber) {
                    draft = c.copy(skinSensitivity = it)
                }
                Spacer(Modifier.height(16.dp))
                LabeledText("محصول جدید استفاده کردی؟")
                ChoiceChips(listOf("نه", "بله"), c.skinNewProduct, accent = Amber) {
                    draft = c.copy(skinNewProduct = it)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // ---------- fatigue ----------
        SectionCard("⚡", "بی‌رمقی و انرژی", "خستگی و علائم همراه", Rose) {
            LabeledText("شدت خستگی امروز")
            ChoiceChips(listOf("خیلی کم", "کم", "متوسط", "زیاد", "خیلی زیاد"), c.fatigueSeverity, accent = Rose) {
                draft = c.copy(fatigueSeverity = it)
                if (it == "زیاد" || it == "خیلی زیاد") showFatigueDetail = true
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showFatigueDetail = !showFatigueDetail }) {
                Text(if (showFatigueDetail) "بستن جزئیات بی‌رمقی" else "جزئیات بیشتر بی‌رمقی ▾")
            }
            if (showFatigueDetail) {
                Spacer(Modifier.height(6.dp))
                LabeledText("سرگیجه")
                ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), c.dizziness, accent = Rose) { draft = c.copy(dizziness = it) }
                Spacer(Modifier.height(16.dp))
                LabeledText("تپش قلب")
                ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), c.palpitations, accent = Rose) {
                    draft = c.copy(palpitations = it)
                }
                Spacer(Modifier.height(16.dp))
                LabeledText("تنگی نفس")
                ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), c.shortBreath, accent = Rose) {
                    draft = c.copy(shortBreath = it)
                }
                Spacer(Modifier.height(16.dp))
                LabeledText("سردرد")
                ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), c.headache, accent = Rose) { draft = c.copy(headache = it) }
                Spacer(Modifier.height(16.dp))
                LabeledText("اشتها")
                ChoiceChips(listOf("خوب", "متوسط", "کم", "خیلی کم", "زیاد"), c.appetite, accent = Rose) {
                    draft = c.copy(appetite = it)
                }
                Spacer(Modifier.height(16.dp))
                LabeledText("فعالیت بدنی")
                ChoiceChips(listOf("ندارم", "کم", "متوسط", "زیاد"), c.physicalActivity, accent = Rose) {
                    draft = c.copy(physicalActivity = it)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // ---------- period ----------
        // The period section is the one place where the rule is strict: the
        // first question is only "are you on your period today?". Answering
        // "no" keeps the section closed and nothing else is asked; answering
        // "yes" opens every period question. Once a period is running (an open
        // period event with no end date), the questions stay open every day
        // until the user marks the period as finished from the ring, after
        // which this returns to the single yes/no question.
        SectionCard("🩷", "پریود", "فقط اگر پریود هستی", Pink) {
            LabeledText("امروز روز پریود هستی؟")
            ChoiceChips(listOf("بله", "نه"), if (c.isPeriodDay) "بله" else "نه", accent = Pink) {
                val yes = it == "بله"
                draft = c.copy(isPeriodDay = yes)
                showPeriodDetail = yes
            }
            if (showPeriodDetail) {
                Spacer(Modifier.height(18.dp))
                LabeledText("امروز درد داری؟")
                ChoiceChips(listOf("ندارم", "دارم"), c.periodPain, accent = Pink) { draft = c.copy(periodPain = it) }
                Spacer(Modifier.height(16.dp))
                LabeledText("شدت درد")
                ChoiceChips(
                    listOf("بدون درد", "خفیف", "متوسط", "شدید", "خیلی شدید"),
                    c.periodPainLevel,
                    accent = Pink
                ) { draft = c.copy(periodPainLevel = it) }
                Spacer(Modifier.height(16.dp))
                LabeledText("محل درد")
                ChoiceChips(listOf("شکم", "کمر", "لگن", "پا", "سر", "چند جا"), c.periodPainLocation, accent = Pink) {
                    draft = c.copy(periodPainLocation = it)
                }
                Spacer(Modifier.height(16.dp))
                LabeledText("خونریزی نسبت به معمول")
                ChoiceChips(listOf("کمتر", "مثل همیشه", "بیشتر", "خیلی بیشتر"), c.periodBleeding, accent = Pink) {
                    draft = c.copy(periodBleeding = it)
                }
                Spacer(Modifier.height(16.dp))
                LabeledText("لخته")
                ChoiceChips(listOf("ندارم", "کم", "زیاد"), c.periodClots, accent = Pink) { draft = c.copy(periodClots = it) }
                Spacer(Modifier.height(16.dp))
                LabeledText("مصرف مسکن")
                ChoiceChips(listOf("نه", "بله"), c.periodMedication, accent = Pink) { draft = c.copy(periodMedication = it) }

                // ---- the three symptom groups, multi-choice ----
                Spacer(Modifier.height(18.dp))
                LabeledText("خون پریودت چه رنگیه؟")
                MultiChoiceChips(
                    options = listOf("قرمز", "قهوه‌ای", "صورتی", "نارنجی"),
                    selected = splitMulti(c.periodBloodColor),
                    accent = Pink
                ) { draft = c.copy(periodBloodColor = joinMulti(it)) }

                Spacer(Modifier.height(18.dp))
                LabeledText("وضعیت تغذیه و گوارشت چطوره؟")
                MultiChoiceChips(
                    options = listOf(
                        "اسهال", "یبوست", "حالت تهوع", "نفخ",
                        "پراشتهایی", "کم‌اشتهایی", "تغییر وزن ناگهانی", "میل به شیرینی"
                    ),
                    selected = splitMulti(c.periodDigestion),
                    accent = Pink
                ) { draft = c.copy(periodDigestion = joinMulti(it)) }

                Spacer(Modifier.height(18.dp))
                LabeledText("ترشحات واژنت از چه نوعیه؟")
                MultiChoiceChips(
                    options = listOf(
                        "چسبناک", "آبکی", "بدبو", "تخم‌مرغی", "غیر عادی",
                        "بدون ترشح", "خشکی واژن", "خارش واژن"
                    ),
                    selected = splitMulti(c.periodDischarge),
                    accent = Pink
                ) { draft = c.copy(periodDischarge = joinMulti(it)) }

                Spacer(Modifier.height(18.dp))
                LabeledText("سرگیجه")
                ChoiceChips(listOf("ندارم", "خفیف", "شدید"), c.periodDizziness, accent = Pink) {
                    draft = c.copy(periodDizziness = it)
                }
                Spacer(Modifier.height(16.dp))
                LabeledText("سردرد")
                ChoiceChips(listOf("ندارم", "خفیف", "شدید"), c.periodHeadache, accent = Pink) {
                    draft = c.copy(periodHeadache = it)
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text(
                    "وقتی «بله» را بزنی، سؤال‌های پریود باز می‌شوند و تا وقتی «پایان پریود» را " +
                            "از حلقهٔ داشبورد ثبت نکنی هر روز همین‌ها پرسیده می‌شوند.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        DastyarCard(accent = Purple) {
            SectionTitle("توضیح بیشتر", "📝")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = c.notes,
                onValueChange = { draft = c.copy(notes = it) },
                placeholder = { Text("اختیاری — هر چیزی که دوست داری بنویس") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2
            )
        }

        Spacer(Modifier.height(20.dp))
        GradientButton("ذخیره وضعیت امروز ✅") {
            vm.saveCheckIn(c.copy(cycleDay = cycleDay))
            vm.generateSuggestion(true)
            onDone()
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun LabeledText(text: String) {
    Text(
        text,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(10.dp))
}
