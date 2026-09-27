package com.dastyar.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Task
import com.dastyar.app.notifications.NotificationHelper
import com.dastyar.app.notifications.ReminderScheduler
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Green
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.ui.theme.Rose
import java.time.LocalDate

private val RED = Color(0xFFF87171)
private val ORANGE = Color(0xFFFB923C)
private val BLUE = Color(0xFF60A5FA)

/**
 * The tasks section.
 *
 * Two paths to add a task: write one ordinary sentence and let the AI pull the
 * date, time and details out of it, or fill the plain manual form. Neither path
 * depends on the other, so the section still works when the AI is unreachable.
 *
 * Reminders are real system alarms (see `ReminderScheduler`), not in-app
 * effects: they fire with the app closed, the screen off and the phone locked,
 * and they come back after a reboot.
 */
@Composable
fun TasksScreen(vm: MainViewModel) {
    val tasks by vm.tasks.collectAsState()
    val context = LocalContext.current

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    // A notification tap asks the app to open this exact task for editing.
    val openTask by vm.openTask.collectAsState()
    LaunchedEffect(openTask) {
        if (openTask > 0L) {
            tasks.firstOrNull { it.id == openTask }?.let { editing = it }
            vm.clearOpenTask()
        }
    }

    // Notification permission (Android 13+) is requested the first time the
    // tasks screen is shown, so a real reminder can actually be delivered.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canPost(context)) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            ScreenHeader(
                emoji = "✅",
                title = "کارهای من",
                subtitle = "کارت را بنویس، یادآوری واقعی بگیر."
            )

            Spacer(Modifier.height(14.dp))

            GradientButton("➕ افزودن کار") {
                editing = null
                adding = true
            }

            Spacer(Modifier.height(14.dp))

            TaskSummary(tasks)

            Spacer(Modifier.height(16.dp))

            if (tasks.isEmpty()) {
                DastyarCard {
                    Text(
                        "هنوز کاری ثبت نکردی. با «➕ افزودن کار» شروع کن؛ می‌توانی فقط " +
                                "یک جمله بنویسی و بقیه‌اش را به من بسپاری.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val (overdue, today, future, done) = groupTasks(tasks)
                TaskGroup("🔴 عقب‌افتاده", RED, overdue, vm) { editing = it }
                TaskGroup("🟠 امروز", ORANGE, today, vm) { editing = it }
                TaskGroup("🔵 آینده", BLUE, future, vm) { editing = it }
                TaskGroup("✅ انجام‌شده", Green, done, vm) { editing = it }
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    if (adding) {
        AddTaskFlow(
            vm = vm,
            onDismiss = { adding = false; vm.clearTaskExtract() },
            onSave = { vm.saveTask(it); adding = false; vm.clearTaskExtract() }
        )
    }

    editing?.let { task ->
        ManualTaskSheet(
            initial = task,
            onDismiss = { editing = null },
            onSave = { vm.saveTask(it); editing = null },
            onDelete = { vm.deleteTask(it); editing = null }
        )
    }
}

// ------------------------------------------------------------------ summary

/** Splits tasks into overdue / today / future / done, each sorted for display. */
private fun groupTasks(tasks: List<Task>): Quadruple {
    val today = Dates.today()
    val overdue = mutableListOf<Task>()
    val todayList = mutableListOf<Task>()
    val future = mutableListOf<Task>()
    val done = mutableListOf<Task>()
    tasks.forEach { t ->
        when {
            t.done -> done += t
            t.date.isBlank() -> future += t
            t.date < today -> overdue += t
            t.date == today -> todayList += t
            else -> future += t
        }
    }
    fun sort(list: List<Task>) = list.sortedWith(
        compareBy({ it.time.ifBlank { "99:99" } }, { it.createdAt })
    )
    return Quadruple(sort(overdue), sort(todayList), sort(future), sort(done))
}

private data class Quadruple(
    val overdue: List<Task>,
    val today: List<Task>,
    val future: List<Task>,
    val done: List<Task>
)

@Composable
private fun TaskSummary(tasks: List<Task>) {
    val (overdue, today, future, done) = groupTasks(tasks)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryPill("🔴 عقب‌افتاده", overdue.size, RED)
        SummaryPill("🟠 امروز", today.size, ORANGE)
        SummaryPill("🔵 آینده", future.size, BLUE)
        SummaryPill("✅ انجام‌شده", done.size, Green)
    }
}

@Composable
private fun SummaryPill(label: String, count: Int, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = .16f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "$label  ${Dates.fa(count)}",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

// --------------------------------------------------------------------- list

@Composable
private fun TaskGroup(
    title: String,
    color: Color,
    items: List<Task>,
    vm: MainViewModel,
    onEdit: (Task) -> Unit
) {
    if (items.isEmpty()) return
    SectionTitle(title, "")
    Spacer(Modifier.height(8.dp))
    items.forEach { t ->
        TaskCard(t, color, vm) { onEdit(t) }
        Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun TaskCard(t: Task, accent: Color, vm: MainViewModel, onEdit: () -> Unit) {
    DastyarCard(accent = if (t.done) Green else accent, onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = t.done,
                onCheckedChange = { vm.toggleTask(t) }
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.title.ifBlank { "کار بدون عنوان" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None
                )
                if (t.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        t.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                val meta = buildString {
                    append(if (t.date.isBlank()) "📅 تعیین نشده" else "📅 ${Dates.pretty(t.date)}")
                    append("  ⏰ ")
                    append(if (t.time.isBlank()) "تعیین نشده" else Dates.faTime(t.time))
                }
                Text(meta, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriorityTag(t.priority)
                    Spacer(Modifier.width(6.dp))
                    if (t.repeat != "none" && t.repeat.isNotBlank()) {
                        Tag(repeatLabel(t.repeat), accent = Cyan)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (t.reminderEnabled) "🔔 یادآوری فعال" else "🔕 بدون یادآوری",
                        fontSize = 11.sp,
                        color = if (t.reminderEnabled)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { vm.deleteTask(t) }) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف")
            }
        }
    }
}

@Composable
private fun PriorityTag(priority: String) {
    when (priority) {
        "high" -> Tag("اولویت بالا", accent = RED)
        "low" -> Tag("کم‌اهمیت", accent = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Tag("معمولی", accent = Purple)
    }
}

private fun repeatLabel(r: String) = when (r) {
    "daily" -> "روزانه"
    "weekly" -> "هفتگی"
    "monthly" -> "ماهانه"
    else -> "بدون تکرار"
}

// ------------------------------------------------------------------ add flow

/**
 * The "add a task" panel. It shows one big writing box with always-visible
 * guidance, asks the AI to read the sentence, and shows a preview the user must
 * confirm. If the AI fails, the same sentence is read by the offline parser and
 * the preview still appears; the manual form is always one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskFlow(
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit
) {
    var sentence by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Task?>(null) }
    var manual by remember { mutableStateOf(false) }

    val extracting by vm.taskExtractLoading.collectAsState()
    val ai by vm.taskExtract.collectAsState()
    val aiError by vm.taskExtractError.collectAsState()
    var localError by remember { mutableStateOf<String?>(null) }

    // The AI answer becomes the preview when it arrives.
    LaunchedEffect(ai) { ai?.let { preview = it } }

    // If the AI could not answer, the same sentence is read offline right away,
    // so the user still gets a preview instead of a dead end.
    LaunchedEffect(aiError, sentence) {
        if (aiError != null && preview == null && sentence.isNotBlank() && !extracting) {
            preview = com.dastyar.app.ai.TaskParser.parse(sentence)
        }
    }

    if (manual) {
        ManualTaskSheet(
            initial = preview,
            onDismiss = onDismiss,
            onSave = onSave,
            onDelete = null
        )
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp)
        ) {
            Text("کار جدید", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))

            Text(
                "✍️ چه کاری داری؟",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = sentence,
                onValueChange = { sentence = it; localError = null },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                minLines = 3,
                maxLines = 6,
                placeholder = {
                    Text("فردا ساعت ۵ عصر قبض برق رو پرداخت کنم", fontSize = 13.sp)
                }
            )

            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .08f))
                    .padding(12.dp)
            ) {
                Column {
                    Text("مثلاً بنویس:", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "«فردا ساعت ۵ عصر قبض برق رو پرداخت کنم»",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "لازم نیست فرم پر کنی؛ فقط مثل همیشه جمله‌ات رو بنویس. " +
                                "من تاریخ، ساعت و جزئیات کار رو برات مشخص می‌کنم.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            GradientButton("🤖 استخراج اطلاعات", enabled = sentence.isNotBlank() && !extracting) {
                if (sentence.isBlank()) localError = "اول جمله‌ات را بنویس."
                else vm.extractTask(sentence)
            }

            if (extracting) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("دارم جمله‌ات را می‌خوانم…", fontSize = 13.sp)
                }
            }

            // If the AI could not answer, the sentence was read offline instead.
            if (aiError != null && preview != null) {
                Spacer(Modifier.height(12.dp))
                DastyarCard(accent = Amber) {
                    Text(
                        "هوش مصنوعی الان در دسترس نیست؛ همین جمله را خودم خواندم. " +
                                "لازم شد می‌توانی با «ویرایش» اصلاح کنی.",
                        fontSize = 12.5.sp
                    )
                }
            }

            localError?.let {
                Spacer(Modifier.height(10.dp))
                Text("⚠️ $it", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error)
            }

            preview?.let { p ->
                Spacer(Modifier.height(16.dp))
                PreviewCard(p)
                Spacer(Modifier.height(12.dp))
                GradientButton("✅ تأیید و ثبت") { onSave(p) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { manual = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✏️ ویرایش")
                }
                if (p.date.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "📅 تاریخ را نگفتی. با «ویرایش» می‌توانی زمان یادآوری را مشخص کنی.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { manual = true }, modifier = Modifier.fillMaxWidth()) {
                Text("فرم دستی (بدون هوش مصنوعی)", fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PreviewCard(p: Task) {
    DastyarCard(accent = Purple) {
        Text("کار جدید", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(p.title.ifBlank { "کار بدون عنوان" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (p.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(p.description, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(if (p.date.isBlank()) "📅 تعیین نشده" else "📅 ${Dates.pretty(p.date)}", fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(if (p.time.isBlank()) "⏰ تعیین نشده" else "⏰ ${Dates.faTime(p.time)}", fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(repeatLabel(p.repeat), fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            if (p.reminderEnabled) "🔔 یادآوری فعال" else "🔕 بدون یادآوری",
            fontSize = 13.sp,
            color = if (p.reminderEnabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ----------------------------------------------------------------- manual form

private val repeats = listOf("بدون تکرار", "روزانه", "هفتگی", "ماهانه")
private val priorities = listOf("معمولی", "بالا", "کم")

/**
 * The plain form. It is the fallback that always works, with or without the AI:
 * title, date, time, repeat, reminder, priority and description are all edited
 * here. The date is picked from a Jalali calendar and stored as an ISO date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTaskSheet(
    initial: Task?,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: ((Task) -> Unit)?
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: "") }
    var time by remember { mutableStateOf(initial?.time ?: "") }
    var repeat by remember { mutableStateOf(initial?.repeat ?: "none") }
    var priority by remember { mutableStateOf(initial?.priority ?: "normal") }
    var reminder by remember { mutableStateOf(initial?.reminderEnabled ?: (initial?.date?.isNotBlank() == true)) }
    var showDatePicker by remember { mutableStateOf(false) }

    // The permission launchers are declared before any lambda that uses them, so
    // the Compose compiler never sees a composable call inside a callback.
    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canPost(context)) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!ReminderScheduler.canScheduleExact(context)) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    )
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp)
        ) {
            Text(
                if (initial == null || initial.id == 0L && initial.title.isBlank())
                    "کار جدید" else "ویرایش کار",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("توضیحات (اختیاری)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2
            )

            Spacer(Modifier.height(12.dp))
            // Date picker from the Jalali calendar.
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (date.isBlank()) "📅 انتخاب تاریخ" else "📅 ${Dates.pretty(date)}")
            }
            if (date.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { date = "" }) { Text("پاک کردن تاریخ", fontSize = 12.sp) }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = if (time.isBlank()) "" else Dates.faTime(time),
                onValueChange = { v -> time = Dates.timeInput(v) },
                label = { Text("ساعت (مثلاً ۱۷:۰۰)") },
                placeholder = { Text("۱۷:۰۰") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            if (time.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { time = "" }) { Text("پاک کردن ساعت", fontSize = 12.sp) }
            }

            Spacer(Modifier.height(14.dp))
            Text("تکرار", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            ChoiceChips(repeats, repeatLabel(repeat)) { label ->
                repeat = when (label) {
                    "روزانه" -> "daily"
                    "هفتگی" -> "weekly"
                    "ماهانه" -> "monthly"
                    else -> "none"
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("اولویت", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            ChoiceChips(priorities, priorityLabel(priority), accent = Amber) { label ->
                priority = when (label) {
                    "بالا" -> "high"
                    "کم" -> "low"
                    else -> "normal"
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = reminder, onCheckedChange = { reminder = it })
                Spacer(Modifier.width(10.dp))
                Text("یادآوری با اعلان گوشی", fontSize = 14.sp)
            }
            if (reminder && (date.isBlank() || time.isBlank())) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "برای یادآوری واقعی، تاریخ و ساعت را هم مشخص کن.",
                    fontSize = 12.sp,
                    color = Amber
                )
            }

            Spacer(Modifier.height(20.dp))
            GradientButton("ذخیره", enabled = title.isNotBlank()) {
                if (reminder) ensurePermissions()
                val clean = initial?.copy(
                    title = title.trim(),
                    description = desc.trim(),
                    date = date.trim(),
                    time = time.trim(),
                    repeat = repeat,
                    priority = priority,
                    reminderEnabled = reminder && date.isNotBlank()
                ) ?: Task(
                    title = title.trim(),
                    description = desc.trim(),
                    date = date.trim(),
                    time = time.trim(),
                    repeat = repeat,
                    priority = priority,
                    reminderEnabled = reminder && date.isNotBlank()
                )
                onSave(clean)
            }

            if (onDelete != null && initial != null && initial.id != 0L) {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { onDelete(initial) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🗑 حذف کار", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showDatePicker) {
        JalaliDatePicker(
            initialIso = date.ifBlank { Dates.today() },
            onDismiss = { showDatePicker = false },
            onPicked = { date = it; showDatePicker = false }
        )
    }
}

private fun priorityLabel(p: String) = when (p) {
    "high" -> "بالا"
    "low" -> "کم"
    else -> "معمولی"
}
