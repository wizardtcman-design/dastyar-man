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
    var showPeriodDetail by remember { mutableStateOf(
        isFirstEver || expectedPeriod || cycleDay in listOf(1, 2)
    ) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text(
            if (isFirstEver) "وضعیت امروزت چطوره؟" else "چک‌این امروز",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            Dates.pretty(todayStr) + if (cycleDay > 0) " • روز ${Dates.fa(cycleDay)} چرخه" else "",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        if (!isFirstEver) {
            Text(
                "چند سؤال کوتاه؛ فقط چیزی که امروز مهمه.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(18.dp))

        // ---------- always asked ----------
        LabeledText("میزان انرژی امروز")
        ChoiceChips(
            listOf("خیلی خوب", "خوب", "متوسط", "کم", "خیلی کم"),
            c.energyLevel
        ) { draft = c.copy(energyLevel = it) }

        Spacer(Modifier.height(16.dp))
        LabeledText("میزان خواب دیشب (ساعت)")
        OutlinedTextField(
            value = if (c.sleepHours == 0f) "" else c.sleepHours.toString(),
            onValueChange = { draft = c.copy(sleepHours = it.toFloatOrNull() ?: 0f) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text("مثلاً 7.5") }
        )

        Spacer(Modifier.height(16.dp))
        LabeledText("کیفیت خواب")
        ChoiceChips(listOf("عالی", "خوب", "متوسط", "ضعیف"), c.sleepQuality) {
            draft = c.copy(sleepQuality = it)
        }

        Spacer(Modifier.height(16.dp))
        LabeledText("آب مصرفی تا الان (لیوان)")
        ChoiceChips((0..12).map { it.toString() }, c.waterGlasses.toString()) {
            draft = c.copy(waterGlasses = it.toIntOrNull() ?: 0)
        }

        Spacer(Modifier.height(16.dp))
        LabeledText("سطح استرس امروز")
        ChoiceChips(listOf("کم", "متوسط", "زیاد", "خیلی زیاد"), c.stressLevel) {
            draft = c.copy(stressLevel = it)
        }

        Spacer(Modifier.height(22.dp))
        // ---------- skin summary + optional detail ----------
        DastyarCard(accent = androidx.compose.ui.graphics.Color(0xFFFBBF24)) {
            SectionTitle("پوست امروز", "✨")
            Spacer(Modifier.height(12.dp))
            LabeledText("وضعیت پوست نسبت به قبل")
            ChoiceChips(listOf("بهتر شده", "مثل قبل", "بدتر شده"), c.skinStatus) {
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
                ChoiceChips(listOf("کمتر شده", "مثل قبل", "بیشتر شده"), c.acneCount) {
                    draft = c.copy(acneCount = it)
                }
                Spacer(Modifier.height(14.dp))
                LabeledText("التهاب یا قرمزی")
                ChoiceChips(listOf("ندارم", "کم", "متوسط", "زیاد"), c.skinInflammation) {
                    draft = c.copy(skinInflammation = it)
                }
                Spacer(Modifier.height(14.dp))
                LabeledText("خشکی یا چربی")
                ChoiceChips(listOf("خشکی", "چربی", "هیچ‌کدام", "هر دو"), c.skinDryOily) {
                    draft = c.copy(skinDryOily = it)
                }
                Spacer(Modifier.height(14.dp))
                LabeledText("سوزش یا حساسیت")
                ChoiceChips(listOf("ندارم", "کم", "زیاد"), c.skinSensitivity) {
                    draft = c.copy(skinSensitivity = it)
                }
                Spacer(Modifier.height(14.dp))
                LabeledText("محصول جدید استفاده کردی؟")
                ChoiceChips(listOf("نه", "بله"), c.skinNewProduct) {
                    draft = c.copy(skinNewProduct = it)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // ---------- fatigue ----------
        DastyarCard(accent = androidx.compose.ui.graphics.Color(0xFFFB7185)) {
            SectionTitle("بی‌رمقی و انرژی", "⚡")
            Spacer(Modifier.height(12.dp))
            LabeledText("شدت خستگی امروز")
            ChoiceChips(listOf("خیلی کم", "کم", "متوسط", "زیاد", "خیلی زیاد"), c.fatigueSeverity) {
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
                ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), c.dizziness) { draft = c.copy(dizziness = it) }
                Spacer(Modifier.height(14.dp))
                LabeledText("تپش قلب")
                ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), c.palpitations) {
                    draft = c.copy(palpitations = it)
                }
                Spacer(Modifier.height(14.dp))
                LabeledText("تنگی نفس")
                ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), c.shortBreath) {
                    draft = c.copy(shortBreath = it)
                }
                Spacer(Modifier.height(14.dp))
                LabeledText("سردرد")
                ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), c.headache) { draft = c.copy(headache = it) }
                Spacer(Modifier.height(14.dp))
                LabeledText("اشتها")
                ChoiceChips(listOf("خوب", "متوسط", "کم", "خیلی کم", "زیاد"), c.appetite) {
                    draft = c.copy(appetite = it)
                }
                Spacer(Modifier.height(14.dp))
                LabeledText("فعالیت بدنی")
                ChoiceChips(listOf("ندارم", "کم", "متوسط", "زیاد"), c.physicalActivity) {
                    draft = c.copy(physicalActivity = it)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // ---------- period ----------
        DastyarCard(accent = androidx.compose.ui.graphics.Color(0xFFEC4899)) {
            SectionTitle("پریود", "🩷")
            Spacer(Modifier.height(12.dp))
            LabeledText("امروز روز پریود هستی؟")
            ChoiceChips(listOf("بله", "نه"), if (c.isPeriodDay) "بله" else "نه") {
                val yes = it == "بله"
                draft = c.copy(isPeriodDay = yes)
                if (yes) showPeriodDetail = true
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showPeriodDetail = !showPeriodDetail }) {
                Text(if (showPeriodDetail) "بستن جزئیات پریود" else "جزئیات بیشتر پریود ▾")
            }
            if (showPeriodDetail) {
                Spacer(Modifier.height(6.dp))
                LabeledText("امروز درد داری؟")
                ChoiceChips(listOf("ندارم", "دارم"), c.periodPain) { draft = c.copy(periodPain = it) }
                Spacer(Modifier.height(14.dp))
                LabeledText("شدت درد")
                ChoiceChips(
                    listOf("بدون درد", "خفیف", "متوسط", "شدید", "خیلی شدید"),
                    c.periodPainLevel
                ) { draft = c.copy(periodPainLevel = it) }
                Spacer(Modifier.height(14.dp))
                LabeledText("محل درد")
                ChoiceChips(listOf("شکم", "کمر", "لگن", "پا", "سر", "چند جا"), c.periodPainLocation) {
                    draft = c.copy(periodPainLocation = it)
                }
                Spacer(Modifier.height(14.dp))
                LabeledText("خونریزی نسبت به معمول")
                ChoiceChips(listOf("کمتر", "مثل همیشه", "بیشتر", "خیلی بیشتر"), c.periodBleeding) {
                    draft = c.copy(periodBleeding = it)
                }
                Spacer(Modifier.height(14.dp))
                LabeledText("لخته")
                ChoiceChips(listOf("ندارم", "کم", "زیاد"), c.periodClots) { draft = c.copy(periodClots = it) }
                Spacer(Modifier.height(14.dp))
                LabeledText("تهوع")
                ChoiceChips(listOf("ندارم", "خفیف", "شدید"), c.periodNausea) { draft = c.copy(periodNausea = it) }
                Spacer(Modifier.height(14.dp))
                LabeledText("سرگیجه")
                ChoiceChips(listOf("ندارم", "خفیف", "شدید"), c.periodDizziness) {
                    draft = c.copy(periodDizziness = it)
                }
                Spacer(Modifier.height(14.dp))
                LabeledText("سردرد")
                ChoiceChips(listOf("ندارم", "خفیف", "شدید"), c.periodHeadache) {
                    draft = c.copy(periodHeadache = it)
                }
                Spacer(Modifier.height(14.dp))
                LabeledText("مصرف مسکن")
                ChoiceChips(listOf("نه", "بله"), c.periodMedication) { draft = c.copy(periodMedication = it) }
            }
        }

        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = c.notes,
            onValueChange = { draft = c.copy(notes = it) },
            label = { Text("توضیح بیشتر (اختیاری)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            minLines = 2
        )

        Spacer(Modifier.height(22.dp))
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
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
}
