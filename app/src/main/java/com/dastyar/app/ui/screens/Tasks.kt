package com.dastyar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Task
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Green

private val repeats = listOf("بدون تکرار", "روزانه", "هفتگی", "ماهانه")

@Composable
fun TasksScreen(vm: MainViewModel) {
    val tasks by vm.tasks.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showSheet = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "کار جدید")
            }
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(18.dp)
        ) {
            ScreenHeader(
                emoji = "✅",
                title = "کارها و یادآوری‌ها",
                subtitle = "کارهایت را بنویس و یادآوری واقعی بگیر."
            )
            Spacer(Modifier.height(16.dp))

            if (tasks.isEmpty()) {
                DastyarCard {
                    Text(
                        "هنوز کاری ثبت نکردی. با دکمه + یک کار جدید بساز.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val pending = tasks.filter { !it.done }
                    val done = tasks.filter { it.done }
                    if (pending.isNotEmpty()) {
                        item { SectionTitle("در انتظار انجام", "🕐") }
                        items(pending) { t ->
                            TaskCard(t, vm) {
                                editing = t
                                showSheet = true
                            }
                        }
                    }
                    if (done.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            SectionTitle("انجام‌شده", "✅")
                        }
                        items(done) { t ->
                            TaskCard(t, vm) {
                                editing = t
                                showSheet = true
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showSheet) {
        TaskSheet(
            initial = editing,
            onDismiss = { showSheet = false },
            onSave = {
                vm.saveTask(it)
                showSheet = false
            }
        )
    }
}

@Composable
private fun TaskCard(t: Task, vm: MainViewModel, onEdit: () -> Unit) {
    DastyarCard(accent = if (t.done) Green else MaterialTheme.colorScheme.primary, onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = t.done, onCheckedChange = { vm.toggleTask(t) })
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None
                )
                if (t.description.isNotBlank()) {
                    Text(
                        t.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val meta = buildString {
                    if (t.date.isNotBlank()) append(Dates.pretty(t.date))
                    if (t.time.isNotBlank()) append(" • ${t.time}")
                    if (t.repeat != "none" && t.repeat.isNotBlank()) {
                        append(" • ${repeatLabel(t.repeat)}")
                    }
                }
                if (meta.isNotBlank()) {
                    Text(meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (t.reminderEnabled) {
                    Text("⏰ یادآوری فعال", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = { vm.deleteTask(t) }) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف")
            }
        }
    }
}

private fun repeatLabel(r: String) = when (r) {
    "daily" -> "روزانه"
    "weekly" -> "هفتگی"
    "monthly" -> "ماهانه"
    else -> "بدون تکرار"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSheet(
    initial: Task?,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var date by remember { mutableStateOf(initial?.date?.ifBlank { Dates.today() } ?: Dates.today()) }
    var time by remember { mutableStateOf(initial?.time ?: "09:00") }
    var repeat by remember { mutableStateOf(initial?.repeat ?: "none") }
    var reminder by remember { mutableStateOf(initial?.reminderEnabled ?: true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                if (initial == null) "کار جدید" else "ویرایش کار",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(16.dp))
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
                label = { Text("توضیحات") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("تاریخ") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("ساعت") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("تکرار", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = reminder, onCheckedChange = { reminder = it })
                Spacer(Modifier.width(10.dp))
                Text("یادآوری با اعلان گوشی", fontSize = 14.sp)
            }
            Spacer(Modifier.height(20.dp))
            GradientButton("ذخیره", enabled = title.isNotBlank()) {
                onSave(
                    (initial ?: Task()).copy(
                        title = title.trim(),
                        description = desc.trim(),
                        date = date.trim(),
                        time = time.trim(),
                        repeat = repeat,
                        reminderEnabled = reminder
                    )
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
