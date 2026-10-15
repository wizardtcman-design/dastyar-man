package com.dastyar.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The single, shared description of what leaves the phone when the AI is used.
 *
 * It is shown both at first-run key entry and in settings, so the user learns
 * the data flow once, clearly, in plain words. It only states facts and changes
 * no behaviour: the app still sends the same requests and every feature keeps
 * working.
 */
private const val AI_PRIVACY_TEXT =
    "برای پاسخ‌دادن، پیام‌های گفتگو و — اگر از این بخش‌ها استفاده کنی — " +
        "جزئیات سلامت (چرخهٔ پریود، علائم، وزن) و تصویری که می‌فرستی، به سرویس " +
        "هوش مصنوعی‌ای که خودت انتخاب کرده‌ای ارسال می‌شوند. دستیار من سرور خودش را " +
        "ندارد و این داده‌ها را جایی جز همین گوشی ذخیره نمی‌کند؛ نگه‌داری آن‌ها در سمت " +
        "سرویس انتخابی، به سیاست همان سرویس بستگی دارد. اگر نمی‌خواهی داده‌ای ارسال شود، " +
        "می‌توانی در تنظیمات، کلید سرویس را حذف کنی."

/** The disclosure text on its own, for placement inside an existing card. */
@Composable
fun AiPrivacyNote(modifier: Modifier = Modifier) {
    Text(
        AI_PRIVACY_TEXT,
        modifier = modifier,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** The disclosure as its own titled card, used in the settings screen. */
@Composable
fun AiPrivacyCard(modifier: Modifier = Modifier) {
    DastyarCard(modifier = modifier) {
        SectionTitle("حریم خصوصی و ارسال داده", "🔐")
        Spacer(Modifier.height(8.dp))
        AiPrivacyNote()
    }
}
