package com.dastyar.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
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

    // filters: "" = all, else overdue/today/future/done
    var filter by remember { mutableStateOf("") }
    var showAddForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    // A notification tap asks the app to open this exact task for editing.
    val openTask by vm.openTask.collectAsState()
    LaunchedEffect(openTask) {
        if (openTask > 0L) {
            tasks.firstOrNull { it.id == openTask }?.let { editing = it }
            vm.clearOpenTask()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canPost(context)) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val groups = remember(tasks) { groupTasks(tasks) }
    val shown: List<Task> = when (filter) {
        "overdue" -> groups.overdue
        "today" -> groups.today
        "future" -> groups.future
        "done" -> groups.done
        else -> tasks.sortedWith(compareBy({ it.done }, { it.date.ifBlank { "9999" } }, { it.time }))
    }

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

        // All four status filters sit in one responsive row (see FilterRow).
        FilterRow(filter, groups) { filter = it }

        Spacer(Modifier.height(14.dp))

        GradientButton(if (showAddForm) "✖ بستن فرم دستی" else "➕ افزودن کار") {
            showAddForm = !showAddForm
        }

        if (showAddForm) {
            Spacer(Modifier.height(12.dp))
            ManualAddCard(
                vm = vm,
                onSaved = { showAddForm = false }
            )
        }

        Spacer(Modifier.height(16.dp))

        // A completely separate section: writing one sentence and letting the AI
        // fill the fields. It is not part of the manual form above.
        AiQuickAddCard(vm = vm)

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
        } else if (shown.isEmpty()) {
            DastyarCard {
                Text(
                    "در این دسته کاری نیست.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            shown.forEach { t ->
                TaskCard(t, accentFor(t, groups), vm) { editing = t }
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(30.dp))
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

/** Colour of a task's card based on which group it belongs to. */
private fun accentFor(t: Task, g: Quadruple): Color = when {
    t.done -> Green
    t.date.isBlank() -> BLUE
    t.date < Dates.today() -> RED
    t.date == Dates.today() -> ORANGE
    else -> BLUE
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

/**
 * The status filters. All four sit in ONE row at equal width: each is
 * `weight(1f)`, so they share the available space and always stay inside the
 * screen on any phone width. The label font and padding are kept small enough
 * that "انجام‌شده" and "عقب‌افتاده" still fit on one line, and the label is
 * allowed to shrink instead of moving to a second row.
 */
@Composable
private fun FilterRow(
    selected: String,
    g: Quadruple,
    onSelect: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChipItem("عقب‌افتاده", "🔴", g.overdue.size, RED, selected == "overdue", Modifier.weight(1f)) {
            onSelect(if (selected == "overdue") "" else "overdue")
        }
        FilterChipItem("امروز", "🟠", g.today.size, ORANGE, selected == "today", Modifier.weight(1f)) {
            onSelect(if (selected == "today") "" else "today")
        }
        FilterChipItem("آینده", "🔵", g.future.size, BLUE, selected == "future", Modifier.weight(1f)) {
            onSelect(if (selected == "future") "" else "future")
        }
        FilterChipItem("انجام‌شده", "✅", g.done.size, Green, selected == "done", Modifier.weight(1f)) {
            onSelect(if (selected == "done") "" else "done")
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    emoji: String,
    count: Int,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (active) color.copy(alpha = .30f) else color.copy(alpha = .15f))
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) color else color.copy(alpha = .30f),
                shape = RoundedCornerShape(13.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            emoji,
            fontSize = 12.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(1.dp))
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = color,
            maxLines = 1,
            softWrap = false
        )
        Text(
            Dates.fa(count),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
    }
}

// --------------------------------------------------------------------- list

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
 * Section 1 — the manual add form, shown inline on the tasks screen. The user
 * fills the fields themselves (title, date, time, repeat, priority, reminder,
 * description) and presses «✅ ثبت کار». It is fully independent of the AI
 * section below it.
 */
@Composable
private fun ManualAddCard(
    vm: MainViewModel,
    onSaved: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("none") }
    var priority by remember { mutableStateOf("normal") }
    var reminder by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canPost(context)) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !ReminderScheduler.canScheduleExact(context)
        ) {
            runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
        }
    }

    DastyarCard(accent = Purple) {
        Text("➕ افزودن کار", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("عنوان کار") },
            placeholder = { Text("پرداخت قبض برق", fontSize = 12.5.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (date.isBlank()) "📅 تاریخ" else "📅 ${Dates.pretty(date)}",
                    fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedTextField(
                value = if (time.isBlank()) "" else Dates.faTime(time),
                onValueChange = { v -> time = Dates.timeInput(v) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                label = { Text("ساعت", fontSize = 12.sp) },
                placeholder = { Text("۱۷:۰۰", fontSize = 12.sp) }
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = desc,
            onValueChange = { desc = it },
            label = { Text("توضیحات (اختیاری)", fontSize = 12.5.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            minLines = 2
        )
        Spacer(Modifier.height(10.dp))
        Text("تکرار", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        ChoiceChips(repeats, repeatLabel(repeat)) { label ->
            repeat = when (label) {
                "روزانه" -> "daily"
                "هفتگی" -> "weekly"
                "ماهانه" -> "monthly"
                else -> "none"
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("اولویت", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        ChoiceChips(priorities, priorityLabel(priority), accent = Amber) { label ->
            priority = when (label) {
                "بالا" -> "high"
                "کم" -> "low"
                else -> "normal"
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = reminder, onCheckedChange = { reminder = it })
            Spacer(Modifier.width(10.dp))
            Text("یادآوری با اعلان گوشی", fontSize = 13.5.sp)
        }
        Spacer(Modifier.height(12.dp))
        GradientButton("✅ ثبت کار", enabled = title.isNotBlank()) {
            if (reminder) ensurePermissions()
            vm.saveTask(
                Task(
                    title = title.trim(),
                    description = desc.trim(),
                    date = date.trim(),
                    time = time.trim(),
                    repeat = repeat,
                    priority = priority,
                    reminderEnabled = reminder && date.isNotBlank()
                )
            )
            onSaved()
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

/**
 * Section 2 — quick add with AI, a separate section from the manual form. The
 * user writes one ordinary sentence, the AI extracts the fields, a preview is
 * shown, and only pressing «✅ تأیید و ثبت» saves it. Nothing is saved on its
 * own. If the AI is unavailable, the same sentence is read offline.
 */
@Composable
private fun AiQuickAddCard(vm: MainViewModel) {
    var sentence by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Task?>(null) }
    var offline by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val extracting by vm.taskExtractLoading.collectAsState()
    val ai by vm.taskExtract.collectAsState()
    val aiError by vm.taskExtractError.collectAsState()

    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canPost(context)) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !ReminderScheduler.canScheduleExact(context)
        ) {
            runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
        }
    }

    LaunchedEffect(ai) { ai?.let { preview = it } }
    LaunchedEffect(aiError, sentence) {
        if (aiError != null && preview == null && sentence.isNotBlank() && !extracting) {
            preview = com.dastyar.app.ai.TaskParser.parse(sentence)
            offline = true
        }
    }

    DastyarCard(accent = Cyan) {
        Text("✨ ثبت سریع با هوش مصنوعی", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = sentence,
            onValueChange = { sentence = it; offline = false },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            minLines = 3,
            maxLines = 6,
            placeholder = {
                Text("مثلاً بنویس: «فردا ساعت ۵ عصر قبض برق رو پرداخت کنم»", fontSize = 12.5.sp)
            }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "لازم نیست فرم پر کنی؛ جمله‌ات را خودمانی بنویس تا تاریخ، ساعت و جزئیات کار را مشخص کنم.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        GradientButton("✨ تبدیل به کار", enabled = sentence.isNotBlank() && !extracting) {
            vm.extractTask(sentence)
        }

        if (extracting) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("دارم جمله‌ات را می‌خوانم…", fontSize = 13.sp)
            }
        }

        if (offline && preview != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "هوش مصنوعی الان در دسترس نبود؛ همین جمله را خودم خواندم.",
                fontSize = 12.sp,
                color = Amber
            )
        }

        preview?.let { p ->
            Spacer(Modifier.height(14.dp))
            PreviewCard(p)
            Spacer(Modifier.height(10.dp))
            GradientButton("✅ تأیید و ثبت") {
                if (p.reminderEnabled) ensurePermissions()
                vm.saveTask(p)
                vm.clearTaskExtract()
                preview = null
                sentence = ""
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    preview = null
                    vm.clearTaskExtract()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("انصراف")
            }
            if (p.date.isBlank() || p.time.isBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "📅 تاریخ یا ساعت را نگفتی، پس یادآوری فعال نشد.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(p: Task) {
    DastyarCard(accent = Purple) {
        Text("پیش‌نمایش کار", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
