package com.dastyar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ai.AiClient
import com.dastyar.app.ai.AiProviders
import com.dastyar.app.ai.CloudflareClient
import com.dastyar.app.ai.PollinationsClient
import com.dastyar.app.ai.ServiceKeys
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Green
import com.dastyar.app.ui.theme.Purple
import kotlinx.coroutines.launch

/**
 * First-run screen. The app ships no API key, so each service is connected here
 * and stored only in the device's private preferences. The services are
 * independent: OpenRouter powers chat, Cloudflare powers images, and
 * Pollinations is the image fallback.
 */
@Composable
fun ConnectGate(onConnected: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var orKey by remember { mutableStateOf("") }
    var orStatus by remember { mutableStateOf<AiClient.ConnectResult?>(null) }
    var orBusy by remember { mutableStateOf(false) }

    var cfAccount by remember { mutableStateOf("") }
    var cfToken by remember { mutableStateOf("") }
    var cfStatus by remember { mutableStateOf<CloudflareClient.CfResult<ByteArray>?>(null) }
    var cfBusy by remember { mutableStateOf(false) }

    var poKey by remember { mutableStateOf("") }
    var poStatus by remember { mutableStateOf<PollinationsClient.PResult?>(null) }
    var poBusy by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        ScreenHeader(
            emoji = "🔌",
            title = "اتصال سرویس‌های هوش مصنوعی",
            subtitle = "کلیدهای خودت را یک‌بار وارد کن. فقط روی همین گوشی ذخیره می‌شوند."
        )

        Spacer(Modifier.height(16.dp))

        // ---------------------------------------------------- OpenRouter
        DastyarCard(accent = Purple) {
            SectionTitle("OpenRouter", "💬")
            Spacer(Modifier.height(6.dp))
            ServiceStatus(ServiceKeys.openRouterState().name)
            Spacer(Modifier.height(8.dp))
            Text(
                "برای چت و همه قابلیت‌های متنی (پیشنهاد روزانه، چرخه، پوست، " +
                        "بی‌رمقی، شرایط پزشکی و آشپزی).",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = orKey,
                onValueChange = { orKey = it.trim() },
                label = { Text("OpenRouter API Key") },
                placeholder = { Text("sk-or-v1-…") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )
            Spacer(Modifier.height(10.dp))
            GradientButton(
                text = if (orBusy) "در حال بررسی…" else "اتصال و بررسی",
                enabled = !orBusy && orKey.isNotBlank()
            ) {
                if (orBusy) return@GradientButton
                orBusy = true; orStatus = null
                scope.launch {
                    val (r, caps) = AiClient.testProvider(AiProviders.openRouter, orKey.trim())
                    orBusy = false; orStatus = r
                    if (r.ok) {
                        ServiceKeys.saveOpenRouter(
                            ctx, orKey.trim(),
                            imageModel = caps?.imageModel ?: AiProviders.openRouter.imageModel,
                            supportsImg = caps?.supportsImage ?: false,
                            supportsEdit = caps?.supportsEdit ?: false
                        )
                        orKey = ""
                    }
                }
            }
            orStatus?.let { st -> StatusLine(st.ok, st.message) }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------------------------------------------- Cloudflare
        DastyarCard(accent = Cyan) {
            SectionTitle("Cloudflare AI", "🎨")
            Spacer(Modifier.height(6.dp))
            ServiceStatus(ServiceKeys.cloudflareState().name)
            Spacer(Modifier.height(8.dp))
            Text(
                "موتور اصلی تولید و ویرایش تصویر. Account ID و API Token را از " +
                        "داشبورد Cloudflare بردار.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = cfAccount,
                onValueChange = { cfAccount = it.trim() },
                label = { Text("Account ID") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cfToken,
                onValueChange = { cfToken = it.trim() },
                label = { Text("API Token") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )
            Spacer(Modifier.height(10.dp))
            GradientButton(
                text = if (cfBusy) "در حال بررسی…" else "اتصال و بررسی",
                enabled = !cfBusy && cfAccount.isNotBlank() && cfToken.isNotBlank()
            ) {
                if (cfBusy) return@GradientButton
                cfBusy = true; cfStatus = null
                scope.launch {
                    // Discover a real image model, then verify it with a request.
                    val models = CloudflareClient.listImageModels(cfAccount.trim(), cfToken.trim())
                    val pick = CloudflareClient.DEFAULT_MODEL.id.takeIf { models.isEmpty() || it in models }
                        ?: models.firstOrNull()
                        ?: CloudflareClient.DEFAULT_MODEL.id
                    val model = CloudflareClient.modelById(pick) ?: CloudflareClient.DEFAULT_MODEL
                    val r = CloudflareClient.test(cfAccount.trim(), cfToken.trim(), model)
                    cfBusy = false; cfStatus = r
                    if (r.ok) {
                        ServiceKeys.saveCloudflare(
                            ctx, cfAccount.trim(), cfToken.trim(), model.id,
                            ServiceKeys.State.CONNECTED
                        )
                        cfStatus = CloudflareClient.CfResult(true, CloudflareClient.CfFail.NONE, "✅ اتصال برقرار است")
                    } else {
                        ServiceKeys.saveCloudflare(
                            ctx, cfAccount.trim(), cfToken.trim(), model.id,
                            ServiceKeys.State.ERROR
                        )
                    }
                }
            }
            cfStatus?.let { st -> StatusLine(st.ok, st.message) }
        }

        Spacer(Modifier.height(14.dp))

        // --------------------------------------------------- Pollinations
        DastyarCard(accent = Green) {
            SectionTitle("Pollinations", "🖼")
            Spacer(Modifier.height(6.dp))
            Text(
                "پشتیبان تولید تصویر. بدون کلید هم کار می‌کند؛ اگر کلید داشته باشی " +
                        "این‌جا وارد کن (اختیاری).",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = poKey,
                onValueChange = { poKey = it.trim() },
                label = { Text("Pollinations API Key (اختیاری)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    if (poBusy) return@OutlinedButton
                    poBusy = true; poStatus = null
                    scope.launch {
                        val r = PollinationsClient.test(poKey.trim().ifBlank { null })
                        poBusy = false; poStatus = r
                        if (r.ok) ServiceKeys.setPollinations(
                            ctx, poKey.trim().ifBlank { null }, ServiceKeys.State.CONNECTED
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = !poBusy
            ) { Text(if (poBusy) "در حال بررسی…" else "اتصال و بررسی") }
            poStatus?.let { st -> StatusLine(st.ok, st.message) }
        }

        Spacer(Modifier.height(18.dp))

        GradientButton(
            text = "ادامه",
            enabled = ServiceKeys.anyConnected()
        ) { onConnected() }

        if (!ServiceKeys.anyConnected()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "برای ادامه، حداقل یکی از سرویس‌ها را وصل کن.",
                fontSize = 11.5.sp,
                color = Amber
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun StatusLine(ok: Boolean, message: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        if (ok) message else "⚠️ $message",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (ok) Green else MaterialTheme.colorScheme.error
    )
}
