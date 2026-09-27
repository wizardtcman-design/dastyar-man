package com.dastyar.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ai.ImageEngine
import com.dastyar.app.ai.QuotaGuard
import com.dastyar.app.ai.ServiceKeys
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.util.GallerySaver
import kotlinx.coroutines.launch

/**
 * One ready-made prompt inside a named group. Tapping a suggestion only fills
 * the prompt box; it never runs the model, so nothing is spent on a mis-tap.
 */
private data class PromptGroup(val title: String, val emoji: String, val prompts: List<String>)

private val generateGroups = listOf(
    PromptGroup("واقع‌گرایانه", "📷", listOf("پرتره حرفه‌ای", "طبیعت", "خودرو")),
    PromptGroup("هنری", "🎨", listOf("نقاشی دیجیتال", "سینمایی", "فانتزی")),
    PromptGroup("تبلیغاتی", "📢", listOf("محصول", "فروشگاه", "شبکه اجتماعی"))
)

/** The actual text behind a short suggestion label, so the box gets a real prompt. */
private fun expandedPrompt(label: String): String = when (label) {
    "پرتره حرفه‌ای" -> "پرتره حرفه‌ای از یک شخص با نور نرم استودیویی و پس‌زمینه ساده"
    "طبیعت" -> "منظره‌ای زیبا از طبیعت، کوه و دریاچه در نور طلایی صبح"
    "خودرو" -> "عکس حرفه‌ای از یک خودروی مدرن در خیابان شبانه"
    "نقاشی دیجیتال" -> "نقاشی دیجیتال رنگارنگ با جزئیات زیاد"
    "سینمایی" -> "صحنه‌ای سینمایی با نورپردازی دراماتیک و عمق میدان"
    "فانتزی" -> "صحنه‌ای فانتزی از قلعه‌ای شناور در آسمان"
    "محصول" -> "عکس تبلیغاتی محصول روی پس‌زمینه سفید تمیز"
    "فروشگاه" -> "بنر تبلیغاتی مدرن برای یک فروشگاه با رنگ‌های گرم"
    "شبکه اجتماعی" -> "پست شبکه اجتماعی با استایل مدرن و مینیمال"
    else -> label
}

/** Ready-made edit instructions; again only fill the box. */
private val editSuggestions = listOf(
    "✨ بهبود کیفیت",
    "💡 بهبود نور",
    "🎨 تغییر سبک",
    "🌅 تغییر پس‌زمینه",
    "👤 بهبود پرتره",
    "📸 حرفه‌ای‌تر کردن عکس"
)

private fun expandedEdit(label: String): String = when (label) {
    "✨ بهبود کیفیت" -> "کیفیت این عکس را بهتر و واضح‌تر کن."
    "💡 بهبود نور" -> "نور این عکس را طبیعی‌تر و متعادل‌تر کن."
    "🎨 تغییر سبک" -> "سبک این عکس را به یک سبک هنری و نقاشی‌گونه تغییر بده."
    "🌅 تغییر پس‌زمینه" -> "پس‌زمینه این عکس را به یک منظره زیبا تغییر بده."
    "👤 بهبود پرتره" -> "چهره و پرتره این عکس را بهبود بده و طبیعی‌تر کن."
    "📸 حرفه‌ای‌تر کردن عکس" -> "این عکس را حرفه‌ای‌تر و چشم‌نوازتر کن."
    else -> label
}

@Composable
fun ImageScreen(vm: MainViewModel) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val saved by vm.savedImages.collectAsState()

    var mode by remember { mutableStateOf("generate") }   // generate / edit
    var prompt by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resultBytes by remember { mutableStateOf<ByteArray?>(null) }
    var lastPrompt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var saveMsg by remember { mutableStateOf<String?>(null) }

    // edit-mode base image (bytes of the picture the user picked / re-picked)
    var baseBytes by remember { mutableStateOf<ByteArray?>(null) }

    val saveToGallery = rememberGallerySaver { saveMsg = it }

    val cfExhausted = QuotaGuard.cloudflareExhaustedToday(ctx)
    val engineName = if (ServiceKeys.cloudflareReady() && !cfExhausted) "Cloudflare AI"
    else "Pollinations"

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        ImageHeader(engineName)

        Spacer(Modifier.height(8.dp))
        ModeTabs(
            mode = mode,
            onSelect = { mode = it; error = null; resultBytes = null }
        )

        Spacer(Modifier.height(12.dp))

        if (mode == "generate") {
            GenerateSection(
                prompt = prompt,
                onPrompt = { prompt = it },
                loading = loading,
                onGenerate = {
                    if (loading) return@GenerateSection
                    if (prompt.isBlank()) { error = "لطفاً توضیح بده چه تصویری می‌خواهی."; return@GenerateSection }
                    loading = true; error = null; note = null
                    scope.launch {
                        lastPrompt = prompt
                        val res = ImageEngine.generate(ctx, prompt)
                        loading = false
                        res.onSuccess {
                            resultBytes = it.bytes; note = it.note
                            vm.saveImage(it.bytes, prompt, "generate")
                        }.onFailure { error = it.message ?: "ساخت تصویر ناموفق بود." }
                    }
                }
            )
        } else {
            EditSection(
                baseBytes = baseBytes,
                onBase = { baseBytes = it },
                prompt = prompt,
                onPrompt = { prompt = it },
                loading = loading,
                onApply = {
                    if (loading) return@EditSection
                    val src = baseBytes
                    if (src == null) { error = "اول یک عکس از گالری انتخاب کن."; return@EditSection }
                    if (prompt.isBlank()) { error = "بگو چه تغییری روی این عکس می‌خواهی."; return@EditSection }
                    loading = true; error = null; note = null
                    scope.launch {
                        lastPrompt = prompt
                        val b64 = android.util.Base64.encodeToString(src, android.util.Base64.NO_WRAP)
                        val res = ImageEngine.edit(ctx, prompt, b64)
                        loading = false
                        res.onSuccess {
                            resultBytes = it.bytes; note = it.note
                            vm.saveImage(it.bytes, prompt, "edit")
                        }.onFailure { error = it.message ?: "ویرایش تصویر ناموفق بود." }
                    }
                }
            )
        }

        // ---- loading / error / note ----
        if (loading) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (mode == "edit") "در حال ویرایش تصویر…" else "در حال ساخت تصویر…",
                    fontSize = 13.sp
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(14.dp))
            DastyarCard(accent = MaterialTheme.colorScheme.error) {
                Text("⚠️ $it", fontSize = 13.sp)
            }
        }
        note?.let {
            Spacer(Modifier.height(14.dp))
            DastyarCard(accent = Purple) { Text(it, fontSize = 12.5.sp, fontWeight = FontWeight.Medium) }
        }
        saveMsg?.let {
            Spacer(Modifier.height(14.dp))
            DastyarCard(accent = MaterialTheme.colorScheme.primary) {
                Text(it, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            }
        }

        // ---- result ----
        resultBytes?.let { bytes ->
            Spacer(Modifier.height(16.dp))
            ResultCard(
                bytes = bytes,
                prompt = lastPrompt,
                onSaveGallery = { saveToGallery(bytes) },
                onRegenerate = {
                    if (loading) return@ResultCard
                    loading = true; error = null; note = null
                    scope.launch {
                        val res = ImageEngine.generate(ctx, lastPrompt)
                        loading = false
                        res.onSuccess {
                            resultBytes = it.bytes; note = it.note
                            vm.saveImage(it.bytes, lastPrompt, "generate")
                        }.onFailure { error = it.message ?: "ساخت تصویر ناموفق بود." }
                    }
                }
            )
        }

        // ---- my images ----
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            SectionTitle("تصاویر من", "🖼")
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(saved, key = { it.id }) { img ->
                    MyImageThumb(
                        bytes = GallerySaver.readFromApp(ctx, img.fileName),
                        onOpen = {
                            GallerySaver.readFromApp(ctx, img.fileName)?.let {
                                resultBytes = it; lastPrompt = img.prompt
                            }
                        },
                        onDelete = { vm.deleteImage(img.id, img.fileName) }
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

/** Compact header, sized like the assistant one; engine name is a small hint. */
@Composable
private fun ImageHeader(engineName: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Purple, Cyan)))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = .22f)),
            contentAlignment = Alignment.Center
        ) { Text("🎨", fontSize = 16.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("تصویر AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text(
                "ساخت و ویرایش تصویر با هوش مصنوعی",
                color = Color.White.copy(alpha = .85f),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            engineName,
            color = Color.White.copy(alpha = .85f),
            fontSize = 9.5.sp,
            maxLines = 1
        )
    }
}

/** Two equal cards that act as tabs. */
@Composable
private fun ModeTabs(mode: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TabCard("🖼", "ساخت تصویر", mode == "generate", Modifier.weight(1f)) { onSelect("generate") }
        TabCard("✏️", "ادیت تصویر", mode == "edit", Modifier.weight(1f)) { onSelect("edit") }
    }
}

@Composable
private fun TabCard(emoji: String, title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Brush.horizontalGradient(listOf(Purple, Cyan))
                else Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .border(
                1.dp,
                if (selected) Color.Transparent else Purple.copy(alpha = .35f),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 15.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ------------------------------------------------------------ generate mode

@Composable
private fun GenerateSection(
    prompt: String,
    onPrompt: (String) -> Unit,
    loading: Boolean,
    onGenerate: () -> Unit
) {
    PromptBox(
        value = prompt,
        onValue = onPrompt,
        placeholder = "توضیح بده چه تصویری می‌خواهی بسازم...",
        accent = Purple
    )

    Spacer(Modifier.height(14.dp))
    SectionTitle("پرامپت‌های آماده", "✨")
    Spacer(Modifier.height(10.dp))
    PromptGroups(groups = generateGroups) { onPrompt(expandedPrompt(it)) }

    Spacer(Modifier.height(14.dp))
    GradientButton(text = if (loading) "…" else "ساخت تصویر 🎨", enabled = !loading, onClick = onGenerate)
}

/**
 * A tidy, non-overflowing list of prompt groups: a horizontally scrolling row
 * of group chips, then that group's cards stacked and wrapped, so a long label
 * can never run off the screen.
 */
@Composable
private fun PromptGroups(groups: List<PromptGroup>, onPick: (String) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(groups) { g ->
            val idx = groups.indexOf(g)
            SelectChip(label = "${g.emoji} ${g.title}", selected = selected == idx, accent = Purple) {
                selected = idx
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    groups[selected].prompts.forEach { p ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(1.dp, Purple.copy(alpha = .22f), RoundedCornerShape(12.dp))
                .clickable { onPick(p) }
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("＋", fontSize = 14.sp, color = Purple)
                Spacer(Modifier.width(8.dp))
                Text(
                    p,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- edit mode

@Composable
private fun EditSection(
    baseBytes: ByteArray?,
    onBase: (ByteArray?) -> Unit,
    prompt: String,
    onPrompt: (String) -> Unit,
    loading: Boolean,
    onApply: () -> Unit
) {
    // Standard Android photo picker — no storage permission is needed for this
    // route on any supported Android version.
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { onBase(it) }
        }
    }

    val base = baseBytes
    if (base == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, Cyan.copy(alpha = .35f), RoundedCornerShape(16.dp))
                .clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = Cyan)
                Spacer(Modifier.height(8.dp))
                Text("انتخاب عکس از گالری", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Spacer(Modifier.height(2.dp))
                Text("برای ویرایش، یک عکس انتخاب کن", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        val bitmap = remember(base) {
            android.graphics.BitmapFactory.decodeByteArray(base, 0, base.size)
        }
        DastyarCard(accent = Cyan) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "عکس انتخاب‌شده",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تعویض عکس")
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("تغییرات آماده", "🪄")
    Spacer(Modifier.height(10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(editSuggestions) { s ->
            SelectChip(label = s, selected = false, accent = Cyan) { onPrompt(expandedEdit(s)) }
        }
    }

    Spacer(Modifier.height(14.dp))
    PromptBox(
        value = prompt,
        onValue = onPrompt,
        placeholder = "بگو چه تغییری روی این عکس می‌خواهی...",
        accent = Cyan
    )

    Spacer(Modifier.height(14.dp))
    GradientButton(
        text = if (loading) "…" else "اعمال تغییرات 🪄",
        enabled = !loading && baseBytes != null,
        onClick = onApply
    )
}

// ---------------------------------------------------------------- pieces

/** The modern rounded prompt box used by both modes. */
@Composable
private fun PromptBox(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    accent: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        shape = RoundedCornerShape(18.dp),
        minLines = 3,
        maxLines = 8,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = accent.copy(alpha = .35f)
        )
    )
}

@Composable
private fun ResultCard(
    bytes: ByteArray,
    prompt: String,
    onSaveGallery: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    val bitmap = remember(bytes) {
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    DastyarCard(accent = Purple) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "نتیجه",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "پرامپت: ${prompt.take(120)}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        GradientButton(text = "ذخیره در گالری ⬇", onClick = { onSaveGallery(prompt) })
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRegenerate,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("تولید دوباره")
        }
    }
}

@Composable
private fun MyImageThumb(bytes: ByteArray?, onOpen: () -> Unit, onDelete: () -> Unit) {
    Box(
        Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Purple.copy(alpha = .25f), RoundedCornerShape(14.dp))
    ) {
        val bitmap = remember(bytes) {
            bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "تصویر من",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpen() },
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("🖼", fontSize = 22.sp)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = .45f))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

/**
 * Saves a result to the shared gallery. On Android 9 and below the legacy
 * WRITE_EXTERNAL_STORAGE permission is requested first; on Android 10+ the
 * MediaStore route writes without any permission. The outcome is reported back
 * through [onMessage] so the screen can show it.
 */
@Composable
private fun rememberGallerySaver(
    onMessage: (String) -> Unit
): (ByteArray) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { mutableStateOf<ByteArray?>(null) }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val bytes = pending.value
        pending.value = null
        if (bytes == null) return@rememberLauncherForActivityResult
        if (!granted) {
            onMessage("دسترسی به حافظه داده نشد؛ اجازه بده تا تصویر در گالری ذخیره شود.")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val err = GallerySaver.saveToGallery(
                ctx, bytes, "dastyar_${System.currentTimeMillis()}.png"
            )
            onMessage(err ?: "تصویر در گالری ذخیره شد.")
        }
    }

    return { bytes ->
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending.value = bytes
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            scope.launch {
                val err = GallerySaver.saveToGallery(
                    ctx, bytes, "dastyar_${System.currentTimeMillis()}.png"
                )
                onMessage(err ?: "تصویر در گالری ذخیره شد.")
            }
        }
    }
}
