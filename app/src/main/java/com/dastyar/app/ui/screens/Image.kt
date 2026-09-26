package com.dastyar.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ai.AiClient
import com.dastyar.app.ai.ImageEngine
import com.dastyar.app.ai.Prompts
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Purple
import kotlinx.coroutines.launch

private data class PromptCat(val title: String, val emoji: String, val prompts: List<String>)

private val categories = listOf(
    PromptCat("پرتره", "👤", listOf(
        "پرتره‌ی هنری از یک زن با نور نرم",
        "پرتره‌ی سیاه‌وسفید کلاسیک",
        "پرتره با نور طلایی غروب"
    )),
    PromptCat("واقع‌گرایانه", "📷", listOf(
        "منظره‌ی کوهستان در مه صبحگاهی",
        "خیابان بارانی شهر در شب",
        "طبیعت‌بی‌جان روی میز چوبی"
    )),
    PromptCat("فانتزی", "🐉", listOf(
        "قلعه‌ی شناور در آسمان",
        "جنگل جادویی با نور آبی",
        "اژدهای مهربان روی ابرها"
    )),
    PromptCat("تبلیغاتی", "📢", listOf(
        "پوستر تبلیغاتی یک نوشیدنی خنک",
        "بنر تبلیغاتی مینیمال مدرن",
        "پوستر فروش ویژه با رنگ گرم"
    )),
    PromptCat("محصول", "📦", listOf(
        "عکس محصول روی پس‌زمینه‌ی سفید",
        "عکس محصول لوکس روی سنگ",
        "عکس محصول با گل‌های طبیعی"
    )),
    PromptCat("هنری", "🎨", listOf(
        "نقاشی آبرنگ از یک باغ ایرانی",
        "طرح مینیمال با خطوط طلایی",
        "هنر انتزاعی با رنگ‌های گرم"
    )),
    PromptCat("والپیپر", "📱", listOf(
        "والپیپر گرادیانت بنفش و آبی",
        "والپیپر مینیمال کوه‌ها",
        "والپیپر کهکشان با ستاره‌ها"
    )),
    PromptCat("شبکه‌های اجتماعی", "📸", listOf(
        "پست اینستاگرام با استایل مدرن",
        "کاور پست با متن کوتاه فارسی",
        "استوری انگیزشی ساده"
    ))
)

@Composable
fun ImageScreen(vm: MainViewModel) {
    val scope = rememberCoroutineScope()
    var selectedCat by remember { mutableIntStateOf(0) }
    var prompt by remember { mutableStateOf(categories[0].prompts[0]) }
    var editInstruction by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var lastPrompt by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var usedNote by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        ScreenHeader(
            emoji = "🎨",
            title = "تصویر AI",
            subtitle = "با هوش مصنوعی تصویر بساز یا تصویر موجود را ویرایش کن."
        )

        Spacer(Modifier.height(16.dp))

        // mode toggle — same chips as the rest of the app
        SingleChoiceChips(
            options = listOf("ساخت تصویر", "ویرایش تصویر"),
            selected = if (isEditing) "ویرایش تصویر" else "ساخت تصویر",
            accent = Purple
        ) { isEditing = (it == "ویرایش تصویر") }

        Spacer(Modifier.height(16.dp))

        if (!isEditing) {
            SectionTitle("پرامپت‌های آماده", "✨")
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    val idx = categories.indexOf(cat)
                    val on = selectedCat == idx
                    SelectChip(
                        label = "${cat.emoji} ${cat.title}",
                        selected = on,
                        accent = Purple
                    ) {
                        selectedCat = idx
                        prompt = cat.prompts[0]
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Column {
                categories[selectedCat].prompts.forEach { p ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (prompt == p) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { prompt = p }
                    ) {
                        Text(
                            p,
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            fontSize = 13.sp,
                            color = if (prompt == p) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("توضیح تصویر (فارسی یا انگلیسی)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2
            )
        } else {
            SectionTitle("ویرایش تصویر", "🪄")
            Spacer(Modifier.height(8.dp))
            Text(
                "اول تصویر پایه را بساز، بعد دستور ویرایش را بنویس. " +
                        "تصویر جدید بر اساس توصیف تو دوباره ساخته می‌شود.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("توصیف تصویر پایه") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = editInstruction,
                onValueChange = { editInstruction = it },
                label = { Text("دستور ویرایش (مثلاً: پس‌زمینه را آبی کن)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2
            )
        }

        Spacer(Modifier.height(16.dp))
        GradientButton(
            text = if (isEditing) "اعمال ویرایش 🪄" else "ساخت تصویر 🎨",
            enabled = !loading
        ) {
            if (loading) return@GradientButton
            loading = true
            error = null
            scope.launch {
                val res: Result<ImageEngine.ImageOutcome> = if (isEditing && imageBytes != null) {
                    // Real image-to-image: send the current picture back to a
                    // model that accepts image input, with the edit text.
                    val instr = if (editInstruction.isNotBlank()) editInstruction else prompt
                    val src = android.util.Base64.encodeToString(
                        imageBytes,
                        android.util.Base64.NO_WRAP
                    )
                    lastPrompt = instr
                    ImageEngine.edit(instr, src)
                } else {
                    val finalPrompt: String =
                        if (isEditing && editInstruction.isNotBlank()) {
                            // No base image yet: ask the text model to turn the
                            // instruction into a full prompt, then render it.
                            val expanded = AiClient.chat(
                                system = Prompts.base(),
                                history = emptyList(),
                                userMessage = Prompts.imageEditPrompt(editInstruction, prompt)
                            ).getOrNull()
                            expanded ?: "$prompt, $editInstruction"
                        } else prompt

                    if (finalPrompt.isBlank()) {
                        error = "لطفاً توصیف تصویر را بنویس."
                        loading = false
                        return@launch
                    }
                    lastPrompt = finalPrompt
                    ImageEngine.generate(finalPrompt)
                }
                loading = false
                res.onSuccess { out ->
                    imageBytes = out.bytes
                    usedNote = out.note
                }.onFailure {
                    error = it.message ?: "ساخت تصویر ناموفق بود."
                }
            }
        }

        if (loading) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("در حال ساخت تصویر… ممکنه تا نیم دقیقه طول بکشه.", fontSize = 13.sp)
            }
        }

        error?.let {
            Spacer(Modifier.height(14.dp))
            DastyarCard(accent = MaterialTheme.colorScheme.error) {
                Text("⚠️ $it", fontSize = 13.sp)
            }
        }

        usedNote?.let { note ->
            Spacer(Modifier.height(14.dp))
            DastyarCard(accent = MaterialTheme.colorScheme.primary) {
                Text(note, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            }
        }

        imageBytes?.let { bytes ->
            Spacer(Modifier.height(18.dp))
            val bitmap = remember(bytes) {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "تصویر ساخته‌شده",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.FillWidth
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "پرامپت: ${lastPrompt.take(120)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (isEditing && editInstruction.isNotBlank()) {
                            prompt = "$prompt, $editInstruction"
                            editInstruction = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ادامه‌ی ویرایش روی همین نتیجه")
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}
