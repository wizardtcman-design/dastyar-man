package com.dastyar.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.audio.VoicePlayer
import com.dastyar.app.data.Profile
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import kotlinx.coroutines.launch

/**
 * First-run, multi-step questionnaire. Collects the personal, period, skin and
 * fatigue baselines, then plays the AI-generated Persian welcome message.
 */
@Composable
fun OnboardingFlow(vm: MainViewModel) {
    var step by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(Profile(id = 1)) }
    var showWelcome by remember { mutableStateOf(false) }

    val total = 5
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

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
        Column(
            Modifier
                .fillMaxSize()
                .padding(22.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (!showWelcome) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "دستیار من 🌱",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "چند سؤال کوتاه تا دستیار تو خوب بشناسدت.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(20.dp))
                StepProgress(step, total)
                Spacer(Modifier.height(22.dp))

                when (step) {
                    0 -> PersonalSection(draft) { draft = it }
                    1 -> PeriodSection(draft) { draft = it }
                    2 -> SkinSection(draft) { draft = it }
                    3 -> FatigueSection(draft) { draft = it }
                    4 -> StepWelcome(draft) { draft = it }
                }

                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("قبلی") }
                    }
                    GradientButton(
                        text = if (step == total - 1) "شروع کنیم 🌱" else "بعدی",
                        modifier = Modifier.weight(1.4f)
                    ) {
                        if (step < total - 1) {
                            step++
                        } else {
                            vm.saveProfile(draft.copy(onboardingDone = true))
                            showWelcome = true
                            scope.launch {
                                VoicePlayer.ensureWelcome(ctx, draft.firstName)
                                    .onSuccess { VoicePlayer.play(it) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            } else {
                WelcomeScreen(
                    name = draft.firstName.ifBlank { "دوست من" },
                    onContinue = { vm.saveProfile(draft.copy(onboardingDone = true)) },
                    onReplay = {
                        scope.launch {
                            VoicePlayer.ensureWelcome(ctx, draft.firstName)
                                .onSuccess { VoicePlayer.play(it) }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PersonalSection(p: Profile, onChange: (Profile) -> Unit) {
    SectionTitle("اطلاعات شخصی", "👤")
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = p.firstName,
        onValueChange = { onChange(p.copy(firstName = it)) },
        label = { Text("نام") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = p.lastName,
        onValueChange = { onChange(p.copy(lastName = it)) },
        label = { Text("نام خانوادگی") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = if (p.age == 0) "" else p.age.toString(),
        onValueChange = { onChange(p.copy(age = it.filter { c -> c.isDigit() }.take(3).toIntOrNull() ?: 0)) },
        label = { Text("سن") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
fun PeriodSection(p: Profile, onChange: (Profile) -> Unit) {
    SectionTitle("پریود", "🩷")
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = p.lastPeriodDate,
        onValueChange = { onChange(p.copy(lastPeriodDate = it)) },
        label = { Text("تاریخ شروع آخرین پریود (مثال 2026-09-10)") },
        placeholder = { Text("2026-09-10") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(16.dp))
    Labeled("طول معمول چرخه (روز)")
    NumberStepper(p.cycleLength, 15, 60) { onChange(p.copy(cycleLength = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("تعداد روزهای معمول پریود")
    NumberStepper(p.periodDays, 1, 12) { onChange(p.copy(periodDays = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("شدت معمول درد (۰ تا ۱۰)")
    ChoiceChips((0..10).map { it.toString() }, p.periodPainLevel.toString()) {
        onChange(p.copy(periodPainLevel = it.toIntOrNull() ?: 0))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("محل درد")
    ChoiceChips(
        listOf("شکم", "کمر", "لگن", "پا", "سر", "چند جا"),
        p.painLocation
    ) { onChange(p.copy(painLocation = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("شدت خونریزی")
    ChoiceChips(listOf("کم", "متوسط", "زیاد", "خیلی زیاد"), p.bleedingLevel) {
        onChange(p.copy(bleedingLevel = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("لخته")
    ChoiceChips(listOf("ندارم", "کم", "زیاد"), p.hasClots) { onChange(p.copy(hasClots = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("تهوع")
    ChoiceChips(listOf("ندارم", "خفیف", "شدید"), p.hasNausea) { onChange(p.copy(hasNausea = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("سرگیجه")
    ChoiceChips(listOf("ندارم", "خفیف", "شدید"), p.hasDizziness) { onChange(p.copy(hasDizziness = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("سردرد")
    ChoiceChips(listOf("ندارم", "خفیف", "شدید"), p.hasHeadache) { onChange(p.copy(hasHeadache = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("کمردرد یا درد لگن")
    ChoiceChips(listOf("ندارم", "خفیف", "متوسط", "شدید"), p.hasBackPain) {
        onChange(p.copy(hasBackPain = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("تأثیر درد روی فعالیت روزانه")
    ChoiceChips(listOf("بدون تأثیر", "کم", "متوسط", "زیاد", "خیلی زیاد"), p.painImpact) {
        onChange(p.copy(painImpact = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("دارو یا روش معمول کنترل درد")
    ChoiceChips(
        listOf("مسکن", "گرم کردن", "استراحت", "دمنوش", "ورزش سبک", "هیچ"),
        p.painRelief
    ) { onChange(p.copy(painRelief = it)) }
}

@Composable
fun SkinSection(p: Profile, onChange: (Profile) -> Unit) {
    SectionTitle("پوست", "✨")
    Spacer(Modifier.height(14.dp))
    Labeled("نوع تقریبی پوست")
    ChoiceChips(listOf("خشک", "چرب", "مختلط", "نرمال", "حساس", "نمی‌دانم"), p.skinType) {
        onChange(p.copy(skinType = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("میزان جوش")
    ChoiceChips(listOf("ندارم", "کم", "متوسط", "زیاد"), p.acneLevel) {
        onChange(p.copy(acneLevel = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("محل جوش")
    ChoiceChips(listOf("پیشانی", "بینی", "گونه", "چانه", "گردن", "پشت"), p.acneLocation) {
        onChange(p.copy(acneLocation = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("جوش زیرپوستی")
    ChoiceChips(listOf("دارم", "ندارم"), p.hasUnderSkinAcne) { onChange(p.copy(hasUnderSkinAcne = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("جوش سرسفید")
    ChoiceChips(listOf("دارم", "ندارم"), p.hasWhiteheads) { onChange(p.copy(hasWhiteheads = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("جوش سرسیاه")
    ChoiceChips(listOf("دارم", "ندارم"), p.hasBlackheads) { onChange(p.copy(hasBlackheads = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("قرمزی و التهاب")
    ChoiceChips(listOf("ندارم", "کم", "متوسط", "زیاد"), p.hasRedness) { onChange(p.copy(hasRedness = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("خشکی یا چربی")
    ChoiceChips(listOf("خشکی", "چربی", "هیچ‌کدام", "هر دو"), p.dryOrOily) { onChange(p.copy(dryOrOily = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("حساسیت یا سوزش")
    ChoiceChips(listOf("ندارم", "کم", "زیاد"), p.hasSensitivity) { onChange(p.copy(hasSensitivity = it)) }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = p.currentProducts,
        onValueChange = { onChange(p.copy(currentProducts = it)) },
        label = { Text("محصولات فعلی پوست") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        minLines = 2
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = p.recentProductChanges,
        onValueChange = { onChange(p.copy(recentProductChanges = it)) },
        label = { Text("تغییرات اخیر محصولات") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        minLines = 2
    )
}

@Composable
fun FatigueSection(p: Profile, onChange: (Profile) -> Unit) {
    SectionTitle("بی‌رمقی", "⚡")
    Spacer(Modifier.height(14.dp))
    Labeled("شدت بی‌رمقی")
    ChoiceChips(listOf("خیلی کم", "کم", "متوسط", "زیاد", "خیلی زیاد"), p.fatigueLevel) {
        onChange(p.copy(fatigueLevel = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("مدت وجود آن")
    ChoiceChips(listOf("چند روز", "یک هفته", "چند هفته", "یک ماه", "بیشتر"), p.fatigueDuration) {
        onChange(p.copy(fatigueDuration = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("کیفیت خواب")
    ChoiceChips(listOf("عالی", "خوب", "متوسط", "ضعیف"), p.sleepQuality) {
        onChange(p.copy(sleepQuality = it))
    }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = if (p.sleepHours == 0f) "" else p.sleepHours.toString(),
        onValueChange = { onChange(p.copy(sleepHours = it.toFloatOrNull() ?: 0f)) },
        label = { Text("میزان خواب (ساعت)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    )
    Spacer(Modifier.height(16.dp))
    Labeled("سرگیجه")
    ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), p.fatigueDizziness) {
        onChange(p.copy(fatigueDizziness = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("تپش قلب")
    ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), p.hasPalpitations) {
        onChange(p.copy(hasPalpitations = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("تنگی نفس")
    ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), p.hasShortBreath) {
        onChange(p.copy(hasShortBreath = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("سردرد")
    ChoiceChips(listOf("ندارم", "گاهی", "زیاد"), p.fatigueHeadache) {
        onChange(p.copy(fatigueHeadache = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("اشتها")
    ChoiceChips(listOf("خوب", "متوسط", "کم", "خیلی کم", "زیاد"), p.appetite) {
        onChange(p.copy(appetite = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("آب مصرفی روزانه (لیوان)")
    NumberStepper(p.waterIntake, 1, 15) { onChange(p.copy(waterIntake = it)) }
    Spacer(Modifier.height(16.dp))
    Labeled("فعالیت بدنی")
    ChoiceChips(listOf("ندارم", "کم", "متوسط", "زیاد"), p.physicalActivity) {
        onChange(p.copy(physicalActivity = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("استرس")
    ChoiceChips(listOf("کم", "متوسط", "زیاد", "خیلی زیاد"), p.stressLevel) {
        onChange(p.copy(stressLevel = it))
    }
    Spacer(Modifier.height(16.dp))
    Labeled("ارتباط احتمالی بی‌رمقی با پریود")
    ChoiceChips(listOf("دارد", "ندارد", "نمی‌دانم"), p.fatiguePeriodLink) {
        onChange(p.copy(fatiguePeriodLink = it))
    }
}

@Composable
private fun StepWelcome(p: Profile, onChange: (Profile) -> Unit) {
    SectionTitle("آخرین قدم", "🌱")
    Spacer(Modifier.height(14.dp))
    Text(
        "بعد از تأیید، یک پیام خوش‌آمدگویی با صدای طبیعی برایت پخش می‌شود.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = p.firstName,
        onValueChange = { onChange(p.copy(firstName = it)) },
        label = { Text("با چه نامی صدایت کنم؟") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun WelcomeScreen(name: String, onContinue: () -> Unit, onReplay: () -> Unit) {
    Spacer(Modifier.height(60.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
            )
            .padding(28.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌱", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "خوش اومدی $name جان",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "من دستیار تو هستم.",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = .92f)
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    OutlinedButton(
        onClick = onReplay,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) { Text("پخش دوباره صدا 🔊") }
    Spacer(Modifier.height(12.dp))
    GradientButton("ورود به داشبورد 🏠") { onContinue() }
}

@Composable
private fun Labeled(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NumberStepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("−") }
        Text(
            value.toString(),
            modifier = Modifier.padding(horizontal = 22.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        FilledTonalButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("+") }
    }
}
