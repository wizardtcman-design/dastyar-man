package com.dastyar.app.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.ui.theme.Cyan

/**
 * The full-screen unlock gate. Draws exactly one of: a numeric keypad (PIN mode)
 * or a 3x3 pattern grid, plus an optional fingerprint button when the user
 * enabled it and the device has a sensor.
 *
 * The screen never touches storage itself; it only reports a code (or an
 * unlock) back to its caller, which decides what to do with it. That keeps the
 * same composable usable both for unlocking and for setting a new code in
 * Settings.
 */
@Composable
fun LockScreen(
    title: String,
    subtitle: String,
    mode: String,
    allowBiometric: Boolean,
    onBiometricRequested: (() -> Unit)? = null,
    error: String? = null,
    onCode: (String) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Text("🔒", fontSize = 40.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.weight(1f))

            if (mode == AppLock.MODE_PATTERN) {
                PatternPad(onCode = onCode)
            } else {
                PinPad(onCode = onCode)
            }

            Spacer(Modifier.weight(1f))

            if (allowBiometric && onBiometricRequested != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onBiometricRequested, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ورود با اثر انگشت", fontSize = 13.5.sp)
                }
            }
            if (onCancel != null) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onCancel) { Text("انصراف", fontSize = 13.sp) }
            }
        }
    }
}

// ------------------------------------------------------------------ PIN

@Composable
private fun PinPad(onCode: (String) -> Unit) {
    var entered by remember { mutableStateOf("") }
    val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "<")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // dots showing how many digits are in
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(maxOf(4, entered.length)) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < entered.length) Purple else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        digits.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { d ->
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                if (d.isBlank()) Color.Transparent
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .then(
                                if (d.isBlank()) Modifier
                                else Modifier.clickable {
                                    when (d) {
                                        "<" -> if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                        else -> {
                                            if (entered.length < 12) entered += d
                                            if (entered.length >= 4) {
                                                // Submit on the 4th digit, the
                                                // usual PIN length; longer codes
                                                // are still accepted by Settings.
                                                onCode(entered)
                                                entered = ""
                                            }
                                        }
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (d) {
                            "" -> {}
                            "<" -> Icon(Icons.Filled.Backspace, contentDescription = "حذف")
                            else -> Text(d, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------- pattern

/**
 * A 3x3 pattern lock. The user drags across dots; the visited cells are recorded
 * as dash-joined indices ("0-1-2-5"). The drawn path is shown live.
 */
@Composable
private fun PatternPad(onCode: (String) -> Unit) {
    val size = 260.dp
    val cells = remember { (0..8).toList() }
    val selected = remember { mutableStateListOf<Int>() }
    var dragPos by remember { mutableStateOf<Offset?>(null) }

    fun addCellIfNear(pos: Offset, boardPx: Float) {
        val cell = boardPx / 3f
        val col = (pos.x / cell).toInt().coerceIn(0, 2)
        val row = (pos.y / cell).toInt().coerceIn(0, 2)
        val idx = row * 3 + col
        if (!selected.contains(idx)) selected.add(idx)
    }

    fun submit() {
        if (selected.size >= 3) onCode(selected.joinToString("-"))
        selected.clear()
        dragPos = null
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .padding(6.dp)
                .pointerInput(Unit) {
                    val boardPx = this.size.width.toFloat()
                    detectDragGestures(
                        onDragStart = { off -> addCellIfNear(off, boardPx) },
                        onDrag = { change, _ -> dragPos = change.position; addCellIfNear(change.position, boardPx) },
                        onDragEnd = { submit() },
                        onDragCancel = { submit() }
                    )
                }
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val cw = w / 3f
                val ch = h / 3f
                fun center(i: Int): Offset {
                    val c = i % 3; val r = i / 3
                    return Offset(cw * c + cw / 2f, ch * r + ch / 2f)
                }
                // connector lines
                for (i in 0 until selected.size - 1) {
                    drawLine(
                        color = Purple,
                        start = center(selected[i]),
                        end = center(selected[i + 1]),
                        strokeWidth = 8f
                    )
                }
                dragPos?.let { p ->
                    if (selected.isNotEmpty()) {
                        drawLine(color = Purple.copy(alpha = .5f), start = center(selected.last()), end = p, strokeWidth = 8f)
                    }
                }
                // dots
                for (i in cells) {
                    val c = center(i)
                    val isOn = selected.contains(i)
                    drawCircle(
                        color = if (isOn) Cyan else Color(0xFF3A4152),
                        radius = if (isOn) 16f else 12f,
                        center = c
                    )
                    drawCircle(
                        color = Purple,
                        radius = 26f,
                        center = c,
                        style = Stroke(width = if (isOn) 6f else 3f)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "الگو را با کشیدن انگشت روی نقطه‌ها بکش (حداقل ۴ نقطه برای تنظیم)",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Shows the system fingerprint / face prompt. Returns true through [onResult]
 * when the user authenticates. Safe to call on devices without a sensor: it
 * simply reports failure.
 */
fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    onResult: (Boolean) -> Unit
) {
    val manager = BiometricManager.from(activity)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
    if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onResult(false)
        return
    }
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onResult(true)
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onResult(false)
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle("برای ورود به دستیار من")
        .setNegativeButtonText("لغو")
        .setAllowedAuthenticators(authenticators)
        .build()
    prompt.authenticate(info)
}
