package com.dastyar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ai.AiClient
import com.dastyar.app.data.Health
import com.dastyar.app.audio.VoicePlayer
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Profile
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Green
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, onClose: () -> Unit) {
    val profile by vm.profile.collectAsState()
    val facts by vm.smartFacts.collectAsState()
    val learningEnabled by vm.learningEnabled.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editPersonal by remember { mutableStateOf(false) }
    var editBody by remember { mutableStateOf(false) }
    var editQuestionnaire by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var darkOverride by remember { mutableStateOf<Boolean?>(null) }
    var notifications by remember { mutableStateOf(true) }
    var soundOn by remember { mutableStateOf(true) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader(
                emoji = "⚙️",
                title = "تنظیمات",
                subtitle = "اطلاعات، اعلان‌ها و وضعیت سرویس",
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "بازگشت")
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- personal info ----
        DastyarCard(accent = MaterialTheme.colorScheme.primary) {
            SectionTitle("اطلاعات شخصی", "👤")
            Spacer(Modifier.height(8.dp))
            val p = profile
            Text("نام: ${p?.firstName?.ifBlank { "—" } ?: "—"}", fontSize = 14.sp)
            Text("نام خانوادگی: ${p?.lastName?.ifBlank { "—" } ?: "—"}", fontSize = 14.sp)
            Text("سن: ${if ((p?.age ?: 0) > 0) Dates.fa(p!!.age) else "—"}", fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { editPersonal = !editPersonal },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("ویرایش اطلاعات شخصی") }
        }

        Spacer(Modifier.height(14.dp))

        // ---- body measurements & medical info ----
        DastyarCard(accent = Green) {
            SectionTitle("قد، وزن و شرایط پزشکی", "⚖️")
            Spacer(Modifier.height(8.dp))
            val p = profile
            val bmi = Health.bmi(p)
            Text("قد: ${if ((p?.heightCm ?: 0) > 0) "${Dates.fa(p!!.heightCm)} سانتی‌متر" else "—"}", fontSize = 14.sp)
            Text("وزن: ${if ((p?.weightKg ?: 0f) > 0f) "${p!!.weightKg} کیلوگرم" else "—"}", fontSize = 14.sp)
            if ((p?.targetWeightKg ?: 0f) > 0f) {
                Text("وزن هدف: ${p!!.targetWeightKg} کیلوگرم", fontSize = 14.sp)
            }
            if (bmi != null) {
                Text(
                    "شاخص توده بدنی: ${"%.1f".format(bmi)}" +
                            Health.bmiCategory(p)?.let { " ($it)" }.orEmpty(),
                    fontSize = 14.sp
                )
            }
            if (!Health.bmiAdultBandsApply(p)) {
                Text(
                    "دسته‌بندی بزرگسالان برای این سن مناسب نیست.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (p?.medicalConditions?.isNotBlank() == true) {
                Text("شرایط: ${p.medicalConditions}", fontSize = 13.sp)
            }
            if (p?.medications?.isNotBlank() == true) {
                Text("داروها: ${p.medications}", fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { editBody = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("ویرایش قد، وزن و شرایط پزشکی") }
        }

        Spacer(Modifier.height(14.dp))

        // ---- smart profile / learning ----
        DastyarCard(accent = MaterialTheme.colorScheme.secondary) {
            SectionTitle("پروفایل هوشمند", "🧠")
            Spacer(Modifier.height(8.dp))
            Text(
                "اگر فعال باشد، دستیار از گفتگوهایت عادت‌ها و ترجیح‌های ساده را یاد می‌گیرد " +
                        "تا پیشنهادها شخصی‌تر شود. هیچ تشخیص روانی یا پزشکی انجام نمی‌شود.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = learningEnabled,
                    onCheckedChange = { vm.setLearningEnabled(it) }
                )
                Spacer(Modifier.width(10.dp))
                Text("یادگیری از گفتگوها", fontSize = 14.sp)
            }
            if (facts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("آنچه تا حالا یاد گرفته:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                facts.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${f.key}: ${f.value}", fontSize = 12.5.sp)
                            Text(
                                "منبع: ${if (f.source == "chat") "گفتگو" else "داده ثبت‌شده"}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { vm.deleteFact(f) }) {
                            Text("حذف", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- questionnaire ----
        DastyarCard(accent = MaterialTheme.colorScheme.secondary) {
            SectionTitle("پرسشنامه", "📋")
            Spacer(Modifier.height(8.dp))
            Text(
                "می‌توانی همه پاسخ‌های اولیه را ویرایش کنی.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { editQuestionnaire = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("ویرایش کامل پرسشنامه") }
        }

        Spacer(Modifier.height(14.dp))

        // ---- notifications & sound ----
        DastyarCard(accent = MaterialTheme.colorScheme.tertiary) {
            SectionTitle("اعلان‌ها و صدا", "🔔")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = notifications, onCheckedChange = { notifications = it })
                Spacer(Modifier.width(10.dp))
                Text("اعلان یادآوری‌ها", fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = soundOn, onCheckedChange = { soundOn = it })
                Spacer(Modifier.width(10.dp))
                Text("صدا و پیام خوش‌آمدگویی", fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        voiceStatus = "در حال ساخت صدا…"
                        val name = profile?.firstName ?: ""
                        VoicePlayer.ensureWelcome(ctx, name)
                            .onSuccess {
                                voiceStatus = "پخش شد 🔊"
                                if (soundOn) VoicePlayer.play(it)
                            }
                            .onFailure { voiceStatus = "خطا: ${it.message}" }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("پخش پیام خوش‌آمدگویی") }
            voiceStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- appearance ----
        DastyarCard(accent = MaterialTheme.colorScheme.primary) {
            SectionTitle("ظاهر", "🎨")
            Spacer(Modifier.height(8.dp))
            Text(
                "حالت نمایش از تنظیمات گوشی پیروی می‌کند. " +
                        "برای تغییر، حالت تاریک یا روشن گوشی را عوض کن.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---- data management ----
        DastyarCard(accent = MaterialTheme.colorScheme.error) {
            SectionTitle("مدیریت داده‌ها", "🗂")
            Spacer(Modifier.height(8.dp))
            Text(
                "همه اطلاعات روی همین گوشی ذخیره می‌شوند و با بستن اپ باقی می‌مانند.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("حذف اطلاعات") }
        }

        Spacer(Modifier.height(14.dp))

        // ---- AI status ----
        DastyarCard {
            SectionTitle("وضعیت هوش مصنوعی", "🤖")
            Spacer(Modifier.height(8.dp))
            Text(
                if (AiClient.chatConfigured) "✅ متصل به سرویس هوش مصنوعی"
                else "⚠️ کلید هوش مصنوعی تنظیم نشده",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "چت، مشاوره و ساخت تصویر با OpenRouter انجام می‌شود. " +
                        "صدای فارسی از سرویس صدا ساخته می‌شود.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---- version ----
        DastyarCard {
            SectionTitle("اطلاعات نسخه", "ℹ️")
            Spacer(Modifier.height(8.dp))
            Text("دستیار من — نسخه ${com.dastyar.app.BuildConfig.VERSION_NAME}", fontSize = 13.sp)
            Text(
                "دستیار من تشخیص پزشکی نمی‌دهد و جایگزین پزشک نیست.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(30.dp))
    }

    if (editPersonal) {
        PersonalEditSheet(profile ?: Profile(id = 1), onDismiss = { editPersonal = false }) {
            vm.saveProfile(it)
            editPersonal = false
        }
    }

    if (editBody) {
        BodyEditSheet(profile ?: Profile(id = 1), onDismiss = { editBody = false }) {
            vm.saveProfile(it)
            if (it.weightKg > 0f) vm.recordWeight(it.weightKg)
            editBody = false
        }
    }

    if (editQuestionnaire) {
        QuestionnaireEditor(profile ?: Profile(id = 1), onDismiss = { editQuestionnaire = false }) {
            vm.saveProfile(it)
            editQuestionnaire = false
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف اطلاعات") },
            text = { Text("همه چک‌این‌ها، کارها و گفت‌وگوها پاک می‌شوند. مطمئنی؟") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllData(keepProfile = true)
                    showDeleteConfirm = false
                }) { Text("حذف کن") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("انصراف") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalEditSheet(p: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var first by remember { mutableStateOf(p.firstName) }
    var last by remember { mutableStateOf(p.lastName) }
    var age by remember { mutableStateOf(if (p.age == 0) "" else p.age.toString()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("ویرایش اطلاعات شخصی", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(first, { first = it }, label = { Text("نام") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(last, { last = it }, label = { Text("نام خانوادگی") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(age, { age = it.filter { ch -> ch.isDigit() }.take(3) },
                label = { Text("سن") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(20.dp))
            GradientButton("ذخیره") {
                onSave(p.copy(firstName = first, lastName = last, age = age.toIntOrNull() ?: 0))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyEditSheet(p: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var height by remember { mutableStateOf(if (p.heightCm == 0) "" else p.heightCm.toString()) }
    var weight by remember { mutableStateOf(if (p.weightKg == 0f) "" else p.weightKg.toString()) }
    var target by remember {
        mutableStateOf(if (p.targetWeightKg == 0f) "" else p.targetWeightKg.toString())
    }
    var conditions by remember { mutableStateOf(p.medicalConditions) }
    var meds by remember { mutableStateOf(p.medications) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("قد، وزن و شرایط پزشکی", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "این اطلاعات برای شاخص توده بدنی و شخصی‌سازی ایمن‌تر پیشنهادهاست و " +
                        "جای تشخیص پزشکی را نمی‌گیرد.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("قد (سانتی‌متر)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("وزن (کیلوگرم)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("وزن هدف (اختیاری)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = conditions,
                onValueChange = { conditions = it },
                label = { Text("بیماری یا شرایط شناخته‌شده") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = meds,
                onValueChange = { meds = it },
                label = { Text("داروهای مهم (اختیاری)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2
            )
            Spacer(Modifier.height(20.dp))
            GradientButton("ذخیره ✅") {
                onSave(
                    p.copy(
                        heightCm = height.toIntOrNull() ?: 0,
                        weightKg = weight.toFloatOrNull() ?: 0f,
                        targetWeightKg = target.toFloatOrNull() ?: 0f,
                        medicalConditions = conditions.trim(),
                        medications = meds.trim()
                    )
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
