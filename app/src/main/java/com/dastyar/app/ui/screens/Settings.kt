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
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import com.dastyar.app.ai.AiClient
import com.dastyar.app.ai.AiProviders
import com.dastyar.app.ai.CloudflareClient
import com.dastyar.app.ai.PollinationsClient
import com.dastyar.app.ai.ServiceKeys
import com.dastyar.app.data.Health
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Profile
import com.dastyar.app.notifications.DailyReminder
import com.dastyar.app.notifications.PeriodReminder
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Green
import com.dastyar.app.ui.theme.Purple
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
    var dailyEnabled by remember { mutableStateOf(DailyReminder.isEnabled(ctx)) }
    var periodEnabled by remember { mutableStateOf(PeriodReminder.isEnabled(ctx)) }
    val dailyTime = remember {
        mutableStateOf("%02d:%02d".format(DailyReminder.hour(ctx), DailyReminder.minute(ctx)))
    }
    var showTimePicker by remember { mutableStateOf(false) }
    var balance by remember { mutableStateOf<AiClient.ServiceBalance?>(null) }
    var balanceLoading by remember { mutableStateOf(false) }
    // OpenRouter card
    var orStatus by remember { mutableStateOf<String?>(null) }
    var orNewKey by remember { mutableStateOf("") }
    var showOrEdit by remember { mutableStateOf(false) }
    // Cloudflare card
    var cfAccount by remember { mutableStateOf("") }
    var cfToken by remember { mutableStateOf("") }
    var cfStatus by remember { mutableStateOf<String?>(null) }
    var showCfEdit by remember { mutableStateOf(false) }
    var cfBusy by remember { mutableStateOf(false) }
    var cfModels by remember { mutableStateOf<List<String>?>(null) }
    // Pollinations card
    var poKey by remember { mutableStateOf("") }
    var poStatus by remember { mutableStateOf<String?>(null) }
    var showPoEdit by remember { mutableStateOf(false) }

    // Refresh the real provider balance as soon as Settings opens.
    LaunchedEffect(Unit) {
        balanceLoading = true
        balance = AiClient.fetchBalance()
        balanceLoading = false
    }

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
                    "شاخص توده بدنی: ${Dates.fa("%.1f".format(bmi))}" +
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

        // ---- daily reminder notification ----
        DastyarCard(accent = MaterialTheme.colorScheme.tertiary) {
            SectionTitle("یادآوری روزانه", "🔔")
            Spacer(Modifier.height(8.dp))
            Text(
                "در ساعت انتخابی تو، اگر وضعیت امروز را ثبت نکرده باشی یک اعلان می‌گیری. " +
                        "بعد از ثبت، اعلان همان روز دیگر نمی‌آید.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = dailyEnabled, onCheckedChange = {
                    dailyEnabled = it
                    DailyReminder.setEnabled(ctx, it)
                    if (it) {
                        com.dastyar.app.notifications.NotificationHelper.createChannels(ctx)
                        DailyReminder.showTestNow(ctx)
                    }
                })
                Spacer(Modifier.width(10.dp))
                Text("یادآوری ثبت وضعیت روزانه", fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("ساعت یادآوری: ${Dates.fa(dailyTime.value)}") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    DailyReminder.showTestNow(ctx)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("نمایش یک اعلان آزمایشی") }
            if (dailyEnabled && !com.dastyar.app.notifications.NotificationHelper.canPost(ctx)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "برای دریافت اعلان، اجازه اعلان را در تنظیمات گوشی فعال کن.",
                    fontSize = 11.5.sp,
                    color = Amber
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- period reminder notification ----
        DastyarCard(accent = MaterialTheme.colorScheme.tertiary) {
            SectionTitle("یادآوری پریود", "🌸")
            Spacer(Modifier.height(8.dp))
            Text(
                "بر اساس تاریخ آخرین پریود و طول چرخه‌ات، ۷ روز و ۳ روز و ۱ روز قبل از " +
                        "پریود بعدی یک اعلان محلی می‌گیری. این اعلان به اینترنت نیاز ندارد " +
                        "و حتی با بسته بودن برنامه هم می‌رسد. زمان‌ها تقریبی‌اند.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = periodEnabled, onCheckedChange = {
                    periodEnabled = it
                    PeriodReminder.setEnabled(ctx, it)
                    if (it) {
                        com.dastyar.app.notifications.NotificationHelper.createChannels(ctx)
                        PeriodReminder.showTestNow(ctx)
                    }
                })
                Spacer(Modifier.width(10.dp))
                Text("یادآوری نزدیک شدن پریود", fontSize = 14.sp)
            }
            if (periodEnabled && profile?.lastPeriodDate.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "برای محاسبهٔ زمان پریود، تاریخ آخرین پریود و طول چرخه را در پروفایل ثبت کن.",
                    fontSize = 11.5.sp,
                    color = Amber
                )
            }
            if (periodEnabled && !com.dastyar.app.notifications.NotificationHelper.canPost(ctx)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "برای دریافت اعلان، اجازه اعلان را در تنظیمات گوشی فعال کن.",
                    fontSize = 11.5.sp,
                    color = Amber
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        DastyarCard(accent = MaterialTheme.colorScheme.primary) {
            SectionTitle("ظاهر و چیدمان", "🎨")
            Spacer(Modifier.height(8.dp))
            Text(
                "چیدمان برنامه راست‌به‌چپ است و حالت نمایش از تنظیمات گوشی پیروی می‌کند. " +
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

        // ---- three AI services, managed independently ----

        // OpenRouter card (chat / text)
        DastyarCard(accent = Purple) {
            SectionTitle("OpenRouter", "💬")
            Spacer(Modifier.height(6.dp))
            ServiceStatus(ServiceKeys.openRouterState().name)
            Spacer(Modifier.height(8.dp))
            Text("وظیفه: چت و همه قابلیت‌های متنی", fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("مدل چت: ${ServiceKeys.openRouterTextModel()}", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (ServiceKeys.openRouterSupportsImage())
                    "مدل تصویر: ${ServiceKeys.openRouterImageModel()}" else "مدل تصویر: ندارد",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("کلید: " + maskKey(ServiceKeys.openRouterKey()), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceButton("تست", Modifier.weight(1f)) {
                    scope.launch {
                        orStatus = "در حال تست واقعی…"
                        val r = AiClient.testConnection(ctx)
                        orStatus = if (r.ok) "✅ اتصال برقرار است" else "⚠️ ${r.message}"
                    }
                }
                ServiceButton("تغییر", Modifier.weight(1f)) { showOrEdit = !showOrEdit }
                ServiceButton("حذف", Modifier.weight(1f)) {
                    ServiceKeys.clearOpenRouter(ctx)
                    orStatus = "کلید OpenRouter حذف شد."
                }
            }
            if (showOrEdit) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = orNewKey, onValueChange = { orNewKey = it.trim() },
                    label = { Text("کلید جدید OpenRouter") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            orStatus = "در حال آزمایش کلید جدید…"
                            val (r, caps) = AiClient.testProvider(AiProviders.openRouter, orNewKey.trim())
                            if (r.ok) {
                                ServiceKeys.saveOpenRouter(
                                    ctx, orNewKey.trim(),
                                    imageModel = caps?.imageModel ?: AiProviders.openRouter.imageModel,
                                    supportsImg = caps?.supportsImage ?: false,
                                    supportsEdit = caps?.supportsEdit ?: false
                                )
                                orNewKey = ""; showOrEdit = false
                                balance = AiClient.fetchBalance()
                                orStatus = "✅ کلید جدید ذخیره شد و از این به بعد استفاده می‌شود"
                            } else orStatus = "⚠️ ${r.message}"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("آزمایش و ذخیره کلید جدید") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    balanceLoading -> "در حال دریافت اعتبار…"
                    balance?.remaining != null -> "اعتبار OpenRouter: ${money(balance!!.remaining!!)}"
                    else -> "اعتبار OpenRouter: قابل دریافت نیست"
                },
                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            orStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Cloudflare card (image engine)
        DastyarCard(accent = Cyan) {
            SectionTitle("Cloudflare AI", "🎨")
            Spacer(Modifier.height(6.dp))
            ServiceStatus(ServiceKeys.cloudflareState().name)
            Spacer(Modifier.height(8.dp))
            Text("وظیفه: تولید و ویرایش تصویر (موتور اصلی)", fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Account ID: " + maskKey(ServiceKeys.cloudflareAccount()), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("API Token: " + maskKey(ServiceKeys.cloudflareToken()), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("مدل تصویر: ${ServiceKeys.cloudflareModel()}", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            cfModels?.let { models ->
                if (models.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text("انتخاب مدل تصویر:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    models.forEach { m ->
                        val on = ServiceKeys.cloudflareModel() == m
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clickable { ServiceKeys.setCloudflareModel(ctx, m) }
                        ) {
                            Text(m, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontSize = 11.sp,
                                color = if (on) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceButton("تست", Modifier.weight(1f)) {
                    scope.launch {
                        cfBusy = true
                        cfStatus = "در حال تست واقعی…"
                        val m = CloudflareClient.modelById(ServiceKeys.cloudflareModel())
                            ?: CloudflareClient.DEFAULT_MODEL
                        val r = CloudflareClient.test(
                            ServiceKeys.cloudflareAccount(), ServiceKeys.cloudflareToken(), m
                        )
                        cfBusy = false
                        cfStatus = if (r.ok) "✅ اتصال برقرار است" else "⚠️ ${r.message}"
                    }
                }
                ServiceButton("تغییر", Modifier.weight(1f)) { showCfEdit = !showCfEdit }
                ServiceButton("حذف", Modifier.weight(1f)) {
                    ServiceKeys.clearCloudflare(ctx)
                    cfModels = null
                    cfStatus = "اطلاعات Cloudflare حذف شد."
                }
            }
            if (showCfEdit) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cfAccount, onValueChange = { cfAccount = it.trim() },
                    label = { Text("Account ID") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp), singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = cfToken, onValueChange = { cfToken = it.trim() },
                    label = { Text("API Token") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            cfStatus = "در حال آزمایش و کشف مدل‌ها…"
                            val models = CloudflareClient.listImageModels(cfAccount.trim(), cfToken.trim())
                            val pick = CloudflareClient.DEFAULT_MODEL.id
                                .takeIf { models.isEmpty() || it in models }
                                ?: models.firstOrNull() ?: CloudflareClient.DEFAULT_MODEL.id
                            val model = CloudflareClient.modelById(pick) ?: CloudflareClient.DEFAULT_MODEL
                            val r = CloudflareClient.test(cfAccount.trim(), cfToken.trim(), model)
                            if (r.ok) {
                                ServiceKeys.saveCloudflare(ctx, cfAccount.trim(), cfToken.trim(), model.id)
                                cfModels = models.ifEmpty { listOf(model.id) }
                                showCfEdit = false
                                cfStatus = "✅ ذخیره شد"
                            } else {
                                ServiceKeys.saveCloudflare(ctx, cfAccount.trim(), cfToken.trim(), model.id,
                                    ServiceKeys.State.ERROR)
                                cfStatus = "⚠️ ${r.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("آزمایش و ذخیره") }
            }

            Spacer(Modifier.height(10.dp))
            Text("مصرف Workers AI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                "سهمیه رایگان روزانه: ${Dates.fa(CloudflareClient.FREE_DAILY_NEURONS)} Neuron " +
                        "(هر روز ریست می‌شود). مصرف دقیق از API قابل دریافت نیست.",
                fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            cfStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Pollinations card (image fallback)
        DastyarCard(accent = Green) {
            SectionTitle("Pollinations", "🖼")
            Spacer(Modifier.height(6.dp))
            ServiceStatus(ServiceKeys.pollinationsState().name)
            Spacer(Modifier.height(8.dp))
            Text("وظیفه: پشتیبان تولید تصویر", fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("مدل تصویر: flux", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("کلید: " + maskKey(ServiceKeys.pollinationsKey()), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceButton("تست", Modifier.weight(1f)) {
                    scope.launch {
                        poStatus = "در حال تست واقعی…"
                        val r = PollinationsClient.test(ServiceKeys.pollinationsKey())
                        if (r.ok) {
                            ServiceKeys.setPollinations(ctx, ServiceKeys.pollinationsKey(),
                                ServiceKeys.State.CONNECTED)
                            poStatus = "✅ اتصال برقرار است"
                        } else poStatus = "⚠️ ${r.message}"
                    }
                }
                ServiceButton("تغییر", Modifier.weight(1f)) { showPoEdit = !showPoEdit }
                ServiceButton("حذف", Modifier.weight(1f)) {
                    ServiceKeys.clearPollinations(ctx)
                    poStatus = "کلید Pollinations حذف شد."
                }
            }
            if (showPoEdit) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = poKey, onValueChange = { poKey = it.trim() },
                    label = { Text("Pollinations API Key (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        ServiceKeys.setPollinations(ctx, poKey.trim().ifBlank { null },
                            ServiceKeys.State.CONNECTED)
                        poKey = ""; showPoEdit = false
                        poStatus = "ذخیره شد"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("ذخیره") }
            }
            poStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

    if (showTimePicker) {
        TimePickerDialog(
            initial = dailyTime.value,
            onDismiss = { showTimePicker = false },
            onPick = { hh, mm ->
                dailyTime.value = "%02d:%02d".format(hh, mm)
                DailyReminder.setTime(ctx, hh, mm)
                showTimePicker = false
            }
        )
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
private fun TimePickerDialog(
    initial: String,
    onDismiss: () -> Unit,
    onPick: (Int, Int) -> Unit
) {
    val parts = initial.split(":")
    var hour by remember { mutableIntStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 20) }
    var minute by remember { mutableIntStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ساعت یادآوری روزانه") },
        text = {
            Column {
                Text(
                    "ساعتی را انتخاب کن که معمولاً بیداری.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("ساعت", fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { hour = (hour + 23) % 24 },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("−") }
                            Text(
                                Dates.fa("%02d".format(hour)),
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            OutlinedButton(
                                onClick = { hour = (hour + 1) % 24 },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("+") }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("دقیقه", fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { minute = (minute + 55) % 60 },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("−") }
                            Text(
                                Dates.fa("%02d".format(minute)),
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            OutlinedButton(
                                onClick = { minute = (minute + 5) % 60 },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("+") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(hour, minute) }) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalEditSheet(p: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var first by remember { mutableStateOf(p.firstName) }
    var last by remember { mutableStateOf(p.lastName) }
    var age by remember { mutableStateOf(Dates.displayField(if (p.age == 0) "" else p.age.toString())) }

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
            OutlinedTextField(
                age,
                { v -> age = Dates.displayField(Dates.digitsOnly(v, 3)) },
                label = { Text("سن") },
                placeholder = { Text("مثلاً ۲۸") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(20.dp))
            GradientButton("ذخیره") {
                onSave(p.copy(firstName = first, lastName = last, age = Dates.parseNum(age)?.toInt() ?: 0))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyEditSheet(p: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var height by remember { mutableStateOf(Dates.displayField(if (p.heightCm == 0) "" else p.heightCm.toString())) }
    var weight by remember { mutableStateOf(Dates.displayField(if (p.weightKg == 0f) "" else p.weightKg.toString())) }
    var target by remember {
        mutableStateOf(Dates.displayField(if (p.targetWeightKg == 0f) "" else p.targetWeightKg.toString()))
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
                    onValueChange = { v -> height = Dates.displayField(Dates.digitsOnly(v, 3)) },
                    label = { Text("قد (سانتی‌متر)") },
                    placeholder = { Text("۱۶۵") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { v -> weight = Dates.displayField(Dates.decimalInput(v, 5)) },
                    label = { Text("وزن (کیلوگرم)") },
                    placeholder = { Text("۶۲") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = target,
                onValueChange = { v -> target = Dates.displayField(Dates.decimalInput(v, 5)) },
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
                        heightCm = Dates.parseNum(height)?.toInt() ?: 0,
                        weightKg = Dates.parseNum(weight) ?: 0f,
                        targetWeightKg = Dates.parseNum(target) ?: 0f,
                        medicalConditions = conditions.trim(),
                        medications = meds.trim()
                    )
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Formats a provider-reported money amount with Persian digits, e.g. "$۹۷٫۴۰". */
private fun money(v: Double): String {
    val s = if (v >= 100) "%.0f".format(v) else "%.2f".format(v)
    return "$" + Dates.fa(s).replace('.', '٫')
}

/** Masks a secret, keeping only the start and the last few characters. */
private fun maskKey(v: String?): String {
    if (v.isNullOrBlank()) return "تنظیم نشده"
    return if (v.length <= 10) "••••" else "${v.take(6)}…${v.takeLast(4)}"
}

/** A compact outlined button used in the service cards. */
@Composable
private fun ServiceButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Text(text, fontSize = 12.sp)
    }
}
