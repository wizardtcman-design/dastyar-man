package com.dastyar.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Green

/**
 * A one-line, honest status for a service. The text comes from the service's
 * stored state, which is only ever set by a real request, so the UI can never
 * show "connected" without a successful call behind it.
 */
@Composable
fun ServiceStatus(stateName: String) {
    val error = MaterialTheme.colorScheme.error
    val (text, color) = when (stateName) {
        "CONNECTED" -> "وضعیت: متصل ✓" to Green
        "BAD_KEY" -> "وضعیت: کلید نامعتبر" to error
        "NO_CREDIT" -> "وضعیت: اعتبار کافی نیست" to Amber
        "FORBIDDEN" -> "وضعیت: دسترسی باز نیست" to error
        "RATE_LIMIT" -> "وضعیت: محدودیت درخواست" to Amber
        "CAPACITY" -> "وضعیت: ظرفیت موقتاً پر است" to Amber
        "MODEL" -> "وضعیت: مدل در دسترس نیست" to Amber
        "NETWORK" -> "وضعیت: خطای شبکه" to error
        "PROVIDER" -> "وضعیت: سرویس در دسترس نیست" to error
        "ERROR" -> "وضعیت: خطا" to error
        else -> "وضعیت: متصل نشده" to Amber
    }
    Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = color)
}
