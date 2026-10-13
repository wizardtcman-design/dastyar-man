package com.dastyar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dastyar.app.BuildConfig
import com.dastyar.app.update.ApkInstaller
import com.dastyar.app.update.UpdateViewModel
import com.dastyar.app.ui.components.DastyarCard
import com.dastyar.app.ui.components.SectionTitle
import com.dastyar.app.ui.theme.Green

/** A Jalali date string for an epoch-millis release time, e.g. "۱۴۰۴/۰۵/۱۲". */
private fun publishedDate(ms: Long): String {
    return try {
        val local = java.time.Instant.ofEpochMilli(ms)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val j = com.dastyar.app.data.Jalali.fromGregorian(local)
        com.dastyar.app.data.Dates.fa("%04d/%02d/%02d".format(j.year, j.month, j.day))
    } catch (_: Exception) {
        ""
    }
}

/**
 * The in-app, fully Persian update screen. It shows the running version, checks
 * for a newer official release, lists its release notes, and walks the user
 * through a real download (with progress, cancel and retry) and a
 * user-confirmed install through Android's own installer.
 */
@Composable
fun UpdateScreen(onClose: () -> Unit, autoCheck: Boolean = true) {
    val ctx = LocalContext.current
    val vm: UpdateViewModel = viewModel()
    val s by vm.state.collectAsState()
    var installError by remember { mutableStateOf<String?>(null) }

    // Checking on open is what the notification tap relies on; a plain manual
    // open from Settings also benefits from a fresh look.
    LaunchedEffect(autoCheck) { if (autoCheck) vm.check(force = true) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "بازگشت")
            }
            Text("به‌روزرسانی", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))

        DastyarCard {
            SectionTitle("نسخه فعلی", "📦")
            Spacer(Modifier.height(6.dp))
            Text("دستیار من — نسخه ${BuildConfig.VERSION_NAME}", fontSize = 14.sp)
        }

        Spacer(Modifier.height(14.dp))

        when (s.phase) {
            UpdateViewModel.Phase.IDLE, UpdateViewModel.Phase.CHECKING -> {
                DastyarCard {
                    SectionTitle("بررسی نسخه تازه", "🔄")
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("در حال بررسی نسخه تازه…", fontSize = 13.sp)
                    }
                }
            }

            UpdateViewModel.Phase.UP_TO_DATE -> {
                DastyarCard(accent = Green) {
                    SectionTitle("به‌روز هستی", "✅")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "نسخه نصب‌شده آخرین نسخه رسمی دستیار من است.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.check(force = true) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("بررسی دوباره")
                    }
                }
            }

            UpdateViewModel.Phase.AVAILABLE,
            UpdateViewModel.Phase.DOWNLOADING,
            UpdateViewModel.Phase.READY,
            UpdateViewModel.Phase.ERROR -> {
                val info = s.update
                if (info == null) {
                    // Should not happen; fall back to the check button.
                    DastyarCard {
                        Button(onClick = { vm.check(force = true) },
                            modifier = Modifier.fillMaxWidth()) { Text("بررسی نسخه تازه") }
                    }
                } else {
                    DastyarCard {
                        SectionTitle("نسخه تازه: ${info.version}", "🎉")
                        if (info.publishedAt > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "تاریخ انتشار: ${publishedDate(info.publishedAt)}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("تغییرات این نسخه:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(info.notesOrDefault(), fontSize = 12.5.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(10.dp))
                        if (info.apkSize > 0) {
                            Text(
                                "حجم فایل: ${"%.1f".format(info.apkSize / 1_048_576.0)} مگابایت",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    when (s.phase) {
                        UpdateViewModel.Phase.DOWNLOADING -> {
                            DastyarCard {
                                SectionTitle("در حال دانلود", "⬇️")
                                Spacer(Modifier.height(10.dp))
                                if (s.totalBytes > 0) {
                                    LinearProgressIndicator(
                                        progress = { (s.percent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    buildString {
                                        append("${s.percent}٪")
                                        if (s.totalBytes > 0) {
                                            append(" — ")
                                            append("%.1f از %.1f مگابایت".format(
                                                s.downloadedBytes / 1_048_576.0,
                                                s.totalBytes / 1_048_576.0
                                            ))
                                        }
                                    },
                                    fontSize = 12.5.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { vm.cancelDownload() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("لغو دانلود") }
                            }
                        }

                        UpdateViewModel.Phase.READY -> {
                            DastyarCard(accent = Green) {
                                SectionTitle("آماده نصب", "📥")
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "فایل دانلود و امضایش با نسخه رسمی بررسی شد. برای نصب، " +
                                            "دکمه زیر را بزن و در صفحه بعد تأیید کن. اطلاعاتت حفظ می‌شود.",
                                    fontSize = 12.5.sp, lineHeight = 20.sp
                                )
                                installError?.let {
                                    Spacer(Modifier.height(8.dp))
                                    Text(it, fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error)
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        installError = null
                                        val file = s.readyFile
                                        if (file == null) {
                                            installError = "فایل نصب پیدا نشد؛ دوباره دانلود کن."
                                        } else if (!ApkInstaller.canInstall(ctx)) {
                                            // First time: send the user to grant
                                            // the "install unknown apps" permission.
                                            ApkInstaller.openInstallPermissionSettings(ctx)
                                            installError = "برای نصب، اجازه «نصب برنامه‌های ناشناس» را به دستیار من بده و برگرد."
                                        } else if (!ApkInstaller.install(ctx, file)) {
                                            installError = "باز کردن نصب‌کننده ممکن نشد."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("نصب به‌روزرسانی")
                                }
                            }
                        }

                        UpdateViewModel.Phase.ERROR -> {
                            DastyarCard {
                                SectionTitle("مشکل در دانلود", "⚠️")
                                Spacer(Modifier.height(8.dp))
                                Text(s.message ?: "دانلود کامل نشد.",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { vm.download() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("تلاش دوباره")
                                }
                            }
                        }

                        else -> {
                            DastyarCard {
                                Button(
                                    onClick = { vm.download() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("دانلود و نصب نسخه ${info.version}")
                                }
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = { vm.skip() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("این نسخه را فعلاً نمی‌خواهم") }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}
