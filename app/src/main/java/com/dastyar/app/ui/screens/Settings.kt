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
import com.dastyar.app.audio.VoicePlayer
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Profile
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, onClose: () -> Unit) {
    val profile by vm.profile.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editPersonal by remember { mutableStateOf(false) }
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
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "بازگشت")
            }
            Text("تنظیمات ⚙️", fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                fontSize = 13.sp
            )
            Text(
                "ساخت تصویر و صدای فارسی از سرویس‌های عمومی استفاده می‌کنند.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---- version ----
        DastyarCard {
            SectionTitle("اطلاعات نسخه", "ℹ️")
            Spacer(Modifier.height(8.dp))
            Text("دستیار من — نسخه ۱.۰.۰", fontSize = 13.sp)
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
