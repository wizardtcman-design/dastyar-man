package com.dastyar.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Jalali
import com.dastyar.app.data.Profile
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Green
import com.dastyar.app.ui.theme.Pink
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.ui.theme.Rose

private val STEP_LABELS = listOf(
    "اطلاعات شخصی",
    "وضعیت پریود",
    "وضعیت پوست",
    "انرژی و بی‌رمقی",
    "آماده‌ای؟"
)

/**
 * First-run questionnaire. Every step is a stack of centred question cards with
 * tappable chips, a real Jalali date picker, and multi-select where it makes
 * sense, so nothing has to be typed by hand.
 */
@Composable
fun OnboardingFlow(vm: MainViewModel) {
    var step by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(Profile(id = 1)) }
    val scrollState = rememberScrollState()

    val total = STEP_LABELS.size
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Whenever the step changes, jump back to the top so the user always starts
    // the new page at the beginning instead of mid-scroll.
    LaunchedEffect(step) {
        scrollState.scrollTo(0)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                Brush.linearGradient(listOf(Purple, Pink))
                            ),
                        contentAlignment = Alignment.Center
                    ) { Text("🌱", fontSize = 23.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "دستیار من",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "چند سؤال کوتاه تا بهتر بشناسمت",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                StepBadge(step, total, STEP_LABELS[step])
                Spacer(Modifier.height(18.dp))

                when (step) {
                    0 -> PersonalSection(draft) { draft = it }
                    1 -> PeriodSection(draft) { draft = it }
                    2 -> SkinSection(draft) { draft = it }
                    3 -> FatigueSection(draft) { draft = it }
                    4 -> FinishSection(draft) { draft = it }
                }
                Spacer(Modifier.height(20.dp))
            }

            // sticky footer with the navigation buttons
            Surface(
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("قبلی") }
                    }
                    GradientButton(
                        text = if (step == total - 1) "شروع کنیم 🌱" else "بعدی ←",
                        modifier = Modifier.weight(if (step > 0) 1.5f else 1f)
                    ) {
                        if (step < total - 1) {
                            step++
                        } else {
                            vm.saveProfile(draft.copy(onboardingDone = true))
                            if (Build.VERSION.SDK_INT >= 33) {
                                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ step 1: personal

@Composable
fun PersonalSection(p: Profile, onChange: (Profile) -> Unit) {
    QuestionCard("👤", "اسمت چیه؟", "با همین اسم صدایت می‌کنم", Purple) {
        OutlinedTextField(
            value = p.firstName,
            onValueChange = { onChange(p.copy(firstName = it)) },
            label = { Text("نام") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("📝", "نام خانوادگی", accent = Purple) {
        OutlinedTextField(
            value = p.lastName,
            onValueChange = { onChange(p.copy(lastName = it)) },
            label = { Text("نام خانوادگی") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🎂", "چند سالته؟", accent = Purple) {
        OutlinedTextField(
            value = Dates.displayField(if (p.age == 0) "" else p.age.toString()),
            onValueChange = {
                onChange(p.copy(age = Dates.digitsOnly(it, 3).toIntOrNull() ?: 0))
            },
            label = { Text("سن") },
            placeholder = { Text("مثلاً ۲۸") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(160.dp),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )
    }
    Spacer(Modifier.height(14.dp))

    // ---- body measurements: used for BMI and personalised water/sleep advice
    QuestionCard("📏", "قد و وزنت چنده؟", "برای محاسبه شاخص توده بدنی و پیشنهاد آب و خواب", Green) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = Dates.displayField(if (p.heightCm == 0) "" else p.heightCm.toString()),
                onValueChange = {
                    onChange(p.copy(heightCm = Dates.digitsOnly(it, 3).toIntOrNull() ?: 0))
                },
                label = { Text("قد (سانتی‌متر)") },
                placeholder = { Text("۱۶۵") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = Dates.displayField(if (p.weightKg == 0f) "" else p.weightKg.toString()),
                onValueChange = { v ->
                    onChange(p.copy(weightKg = Dates.decimalInput(v, 5).toFloatOrNull() ?: 0f))
                },
                label = { Text("وزن (کیلوگرم)") },
                placeholder = { Text("۶۲") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = Dates.displayField(if (p.targetWeightKg == 0f) "" else p.targetWeightKg.toString()),
            onValueChange = { v ->
                onChange(p.copy(targetWeightKg = Dates.decimalInput(v, 5).toFloatOrNull() ?: 0f))
            },
            label = { Text("وزن هدف (اختیاری)") },
            placeholder = { Text("۵۸") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )
    }
    Spacer(Modifier.height(14.dp))

    // ---- self-declared medical info; optional by design
    QuestionCard(
        "🩺", "شرایط پزشکی یا دارویی داری؟",
        "اختیاری — فقط اگر دوست داری بنویس. برای ایمن‌تر شدن پیشنهادها استفاده می‌شود.", Amber
    ) {
        OutlinedTextField(
            value = p.medicalConditions,
            onValueChange = { onChange(p.copy(medicalConditions = it)) },
            label = { Text("بیماری یا شرایط شناخته‌شده") },
            placeholder = { Text("مثلاً: کم‌خونی، تیروئید") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            minLines = 2
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = p.medications,
            onValueChange = { onChange(p.copy(medications = it)) },
            label = { Text("داروهای مهم یا مداوم (اختیاری)") },
            placeholder = { Text("مثلاً: قرص آهن") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            minLines = 2
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "این اپ تشخیص پزشکی نمی‌دهد و این اطلاعات فقط برای شخصی‌سازی ایمن‌تر پیشنهادهاست.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// -------------------------------------------------------------- step 2: period

@Composable
fun PeriodSection(p: Profile, onChange: (Profile) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        JalaliDatePicker(
            initialIso = p.lastPeriodDate.ifBlank { null },
            onDismiss = { showPicker = false },
            onPicked = {
                onChange(p.copy(lastPeriodDate = it))
                showPicker = false
            }
        )
    }

    QuestionCard("🩷", "آخرین پریودت کِی شروع شد؟", "با تقویم شمسی انتخاب کن", Pink) {
        Surface(
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true }
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📅", fontSize = 19.sp)
                Spacer(Modifier.width(11.dp))
                Text(
                    if (p.lastPeriodDate.isBlank()) "انتخاب تاریخ"
                    else Jalali.pretty(p.lastPeriodDate),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (p.lastPeriodDate.isBlank())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "تغییر",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🔁", "طول معمول چرخه‌ات چند روزه؟", accent = Pink) {
        NiceStepper(p.cycleLength, 15, 60, Pink, "روز") { onChange(p.copy(cycleLength = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("📆", "پریودت معمولاً چند روز طول می‌کشه؟", accent = Pink) {
        NiceStepper(p.periodDays, 1, 12, Pink, "روز") { onChange(p.copy(periodDays = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("😣", "شدت معمول دردت چقدره؟", "۰ یعنی بدون درد، ۱۰ یعنی خیلی شدید", Pink) {
        SingleChoiceChips(
            options = (0..10).map { Dates.fa(it) },
            selected = Dates.fa(p.periodPainLevel),
            accent = Pink
        ) { onChange(p.copy(periodPainLevel = Dates.parseNum(it)?.toInt() ?: 0)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("📍", "دردت معمولاً کجاست؟", "می‌تونی چند مورد انتخاب کنی", Pink) {
        MultiChoiceChips(
            options = listOf("شکم", "کمر", "لگن", "پا", "سر", "چند جا"),
            selected = splitMulti(p.painLocation),
            accent = Pink
        ) { onChange(p.copy(painLocation = joinMulti(it))) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🩸", "شدت خونریزیت چطوره؟", accent = Pink) {
        SingleChoiceChips(
            options = listOf("کم", "متوسط", "زیاد", "خیلی زیاد"),
            selected = p.bleedingLevel,
            accent = Pink
        ) { onChange(p.copy(bleedingLevel = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🔴", "لخته داری؟", accent = Pink) {
        SingleChoiceChips(
            options = listOf("ندارم", "کم", "زیاد"),
            selected = p.hasClots,
            accent = Pink
        ) { onChange(p.copy(hasClots = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🤢", "تهوع داری؟", accent = Pink) {
        SingleChoiceChips(
            options = listOf("ندارم", "خفیف", "شدید"),
            selected = p.hasNausea,
            accent = Pink
        ) { onChange(p.copy(hasNausea = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("💫", "سرگیجه داری؟", accent = Pink) {
        SingleChoiceChips(
            options = listOf("ندارم", "خفیف", "شدید"),
            selected = p.hasDizziness,
            accent = Pink
        ) { onChange(p.copy(hasDizziness = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🤕", "سردرد داری؟", accent = Pink) {
        SingleChoiceChips(
            options = listOf("ندارم", "خفیف", "شدید"),
            selected = p.hasHeadache,
            accent = Pink
        ) { onChange(p.copy(hasHeadache = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🦴", "کمردرد یا درد لگن داری؟", accent = Pink) {
        SingleChoiceChips(
            options = listOf("ندارم", "خفیف", "متوسط", "شدید"),
            selected = p.hasBackPain,
            accent = Pink
        ) { onChange(p.copy(hasBackPain = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🚶", "درد چقدر روی فعالیت روزانه‌ات اثر می‌ذاره؟", accent = Pink) {
        SingleChoiceChips(
            options = listOf("بدون تأثیر", "کم", "متوسط", "زیاد", "خیلی زیاد"),
            selected = p.painImpact,
            accent = Pink
        ) { onChange(p.copy(painImpact = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("💊", "معمولاً با چی دردت رو کنترل می‌کنی؟", "چند مورد هم اشکالی نداره", Pink) {
        MultiChoiceChips(
            options = listOf("مسکن", "گرم کردن", "استراحت", "دمنوش", "ورزش سبک", "هیچ"),
            selected = splitMulti(p.painRelief),
            accent = Pink
        ) { onChange(p.copy(painRelief = joinMulti(it))) }
    }
}

// ---------------------------------------------------------------- step 3: skin

@Composable
fun SkinSection(p: Profile, onChange: (Profile) -> Unit) {
    QuestionCard("✨", "نوع پوستت چیه؟", accent = Amber) {
        MultiChoiceChips(
            options = listOf("خشک", "چرب", "مختلط", "نرمال", "حساس", "نمی‌دانم"),
            selected = splitMulti(p.skinType),
            accent = Amber
        ) { onChange(p.copy(skinType = joinMulti(it))) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🔴", "میزان جوشت چقدره؟", accent = Amber) {
        SingleChoiceChips(
            options = listOf("ندارم", "کم", "متوسط", "زیاد"),
            selected = p.acneLevel,
            accent = Amber
        ) { onChange(p.copy(acneLevel = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("📍", "جوش‌ها کجاها بیشتر هستن؟", "می‌تونی چند مورد انتخاب کنی", Amber) {
        MultiChoiceChips(
            options = listOf("پیشانی", "بینی", "گونه", "چانه", "گردن", "پشت"),
            selected = splitMulti(p.acneLocation),
            accent = Amber
        ) { onChange(p.copy(acneLocation = joinMulti(it))) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🫧", "جوش زیرپوستی داری؟", accent = Amber) {
        SingleChoiceChips(
            options = listOf("دارم", "ندارم"),
            selected = p.hasUnderSkinAcne,
            accent = Amber
        ) { onChange(p.copy(hasUnderSkinAcne = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("⚪", "جوش سرسفید داری؟", accent = Amber) {
        SingleChoiceChips(
            options = listOf("دارم", "ندارم"),
            selected = p.hasWhiteheads,
            accent = Amber
        ) { onChange(p.copy(hasWhiteheads = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("⚫", "جوش سرسیاه داری؟", accent = Amber) {
        SingleChoiceChips(
            options = listOf("دارم", "ندارم"),
            selected = p.hasBlackheads,
            accent = Amber
        ) { onChange(p.copy(hasBlackheads = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🌡", "قرمزی و التهاب داری؟", accent = Amber) {
        SingleChoiceChips(
            options = listOf("ندارم", "کم", "متوسط", "زیاد"),
            selected = p.hasRedness,
            accent = Amber
        ) { onChange(p.copy(hasRedness = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("💧", "پوستت خشک‌تره یا چرب؟", accent = Amber) {
        SingleChoiceChips(
            options = listOf("خشکی", "چربی", "هیچ‌کدام", "هر دو"),
            selected = p.dryOrOily,
            accent = Amber
        ) { onChange(p.copy(dryOrOily = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🌶", "حساسیت یا سوزش داری؟", accent = Amber) {
        SingleChoiceChips(
            options = listOf("ندارم", "کم", "زیاد"),
            selected = p.hasSensitivity,
            accent = Amber
        ) { onChange(p.copy(hasSensitivity = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🧴", "الان چه محصولاتی استفاده می‌کنی؟", "اختیاری — اسمشون رو بنویس", Amber) {
        OutlinedTextField(
            value = p.currentProducts,
            onValueChange = { onChange(p.copy(currentProducts = it)) },
            label = { Text("محصولات فعلی") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            minLines = 2
        )
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🔄", "اخیراً محصولت رو عوض کردی؟", "اختیاری", Amber) {
        OutlinedTextField(
            value = p.recentProductChanges,
            onValueChange = { onChange(p.copy(recentProductChanges = it)) },
            label = { Text("تغییرات اخیر") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            minLines = 2
        )
    }
}

// ------------------------------------------------------------- step 4: fatigue

@Composable
fun FatigueSection(p: Profile, onChange: (Profile) -> Unit) {
    QuestionCard("⚡", "شدت بی‌رمقی‌ات چقدره؟", accent = Rose) {
        SingleChoiceChips(
            options = listOf("خیلی کم", "کم", "متوسط", "زیاد", "خیلی زیاد"),
            selected = p.fatigueLevel,
            accent = Rose
        ) { onChange(p.copy(fatigueLevel = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("⏳", "چند وقته این حالت رو داری؟", accent = Rose) {
        SingleChoiceChips(
            options = listOf("چند روز", "یک هفته", "چند هفته", "یک ماه", "بیشتر"),
            selected = p.fatigueDuration,
            accent = Rose
        ) { onChange(p.copy(fatigueDuration = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("😴", "کیفیت خوابت چطوره؟", accent = Cyan) {
        SingleChoiceChips(
            options = listOf("عالی", "خوب", "متوسط", "ضعیف"),
            selected = p.sleepQuality,
            accent = Cyan
        ) { onChange(p.copy(sleepQuality = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🛏", "معمولاً چند ساعت می‌خوابی؟", accent = Cyan) {
        OutlinedTextField(
            value = Dates.displayField(if (p.sleepHours == 0f) "" else p.sleepHours.toString()),
            onValueChange = { onChange(p.copy(sleepHours = Dates.decimalInput(it, 4).toFloatOrNull() ?: 0f)) },
            label = { Text("ساعت") },
            placeholder = { Text("مثلاً ۷.۵") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(160.dp),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("💫", "سرگیجه داری؟", accent = Rose) {
        SingleChoiceChips(
            options = listOf("ندارم", "گاهی", "زیاد"),
            selected = p.fatigueDizziness,
            accent = Rose
        ) { onChange(p.copy(fatigueDizziness = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("❤️", "تپش قلب داری؟", accent = Rose) {
        SingleChoiceChips(
            options = listOf("ندارم", "گاهی", "زیاد"),
            selected = p.hasPalpitations,
            accent = Rose
        ) { onChange(p.copy(hasPalpitations = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🫁", "تنگی نفس داری؟", accent = Rose) {
        SingleChoiceChips(
            options = listOf("ندارم", "گاهی", "زیاد"),
            selected = p.hasShortBreath,
            accent = Rose
        ) { onChange(p.copy(hasShortBreath = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🤕", "سردرد داری؟", accent = Rose) {
        SingleChoiceChips(
            options = listOf("ندارم", "گاهی", "زیاد"),
            selected = p.fatigueHeadache,
            accent = Rose
        ) { onChange(p.copy(fatigueHeadache = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🍽", "اشتهایت چطوره؟", accent = Green) {
        SingleChoiceChips(
            options = listOf("خوب", "متوسط", "کم", "خیلی کم", "زیاد"),
            selected = p.appetite,
            accent = Green
        ) { onChange(p.copy(appetite = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("💧", "روزانه چند لیوان آب می‌نوشی؟", accent = Cyan) {
        NiceStepper(p.waterIntake, 1, 15, Cyan, "لیوان") { onChange(p.copy(waterIntake = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🏃", "فعالیت بدنی‌ات چقدره؟", accent = Green) {
        SingleChoiceChips(
            options = listOf("ندارم", "کم", "متوسط", "زیاد"),
            selected = p.physicalActivity,
            accent = Green
        ) { onChange(p.copy(physicalActivity = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🧘", "سطح استرست چقدره؟", accent = Green) {
        SingleChoiceChips(
            options = listOf("کم", "متوسط", "زیاد", "خیلی زیاد"),
            selected = p.stressLevel,
            accent = Green
        ) { onChange(p.copy(stressLevel = it)) }
    }
    Spacer(Modifier.height(14.dp))

    QuestionCard("🩷", "فکر می‌کنی بی‌رمقی‌ات با پریود مرتبطه؟", accent = Pink) {
        SingleChoiceChips(
            options = listOf("دارد", "ندارد", "نمی‌دانم"),
            selected = p.fatiguePeriodLink,
            accent = Pink
        ) { onChange(p.copy(fatiguePeriodLink = it)) }
    }
}

// -------------------------------------------------------------- step 5: finish

@Composable
fun FinishSection(p: Profile, onChange: (Profile) -> Unit) {
    QuestionCard("🌱", "با چه اسمی صدایت کنم؟", "با همین اسم صدایت می‌کنم", Green) {
        OutlinedTextField(
            value = p.firstName,
            onValueChange = { onChange(p.copy(firstName = it)) },
            label = { Text("نام") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )
    }
}

// ---------------------------------------------------------------- welcome end

