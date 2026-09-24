package com.dastyar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.dastyar.app.data.Dates
import com.dastyar.app.data.Jalali
import com.dastyar.app.ui.theme.Purple

/**
 * Persian (Jalali) date picker: choose year, month and day from real
 * selectors, so the user never types a date by hand.
 */
@Composable
fun JalaliDatePicker(
    initialIso: String?,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit
) {
    val today = Jalali.today()
    var year by remember { mutableIntStateOf(Jalali.parse(initialIso ?: "")?.year ?: today.year) }
    var month by remember { mutableIntStateOf(Jalali.parse(initialIso ?: "")?.month ?: today.month) }
    var day by remember { mutableIntStateOf(Jalali.parse(initialIso ?: "")?.day ?: today.day) }

    val maxDay = Jalali.monthLength(year, month)
    LaunchedEffect(year, month) {
        if (day > maxDay) day = maxDay
    }

    val years = remember { Jalali.yearRange() }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = null,
        text = {
            Column(Modifier.fillMaxWidth()) {
                // header
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
                        )
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "انتخاب تاریخ",
                            color = Color.White.copy(alpha = .9f),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${Dates.fa(day)} ${Jalali.monthName(month)} ${Dates.fa(year)}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "امروز: ${Jalali.pretty(today.iso())}",
                            color = Color.White.copy(alpha = .8f),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // --- year ---
                PickerLabel("سال")
                Spacer(Modifier.height(7.dp))
                val yearState = rememberLazyListState(
                    initialFirstVisibleItemIndex = years.indexOf(year).coerceAtLeast(0)
                )
                LazyRow(
                    state = yearState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(years) { y ->
                        SelectChip(
                            label = Dates.fa(y.toString()),
                            selected = y == year,
                            accent = Purple
                        ) { year = y }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // --- month ---
                PickerLabel("ماه")
                Spacer(Modifier.height(7.dp))
                // two rows of six months keeps everything visible without scrolling
                Jalali.months().chunked(6).forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEachIndexed { idx, name ->
                            val m = Jalali.months().indexOf(name) + 1
                            Box(Modifier.weight(1f)) {
                                SelectChip(
                                    label = name,
                                    selected = m == month,
                                    accent = Purple,
                                    modifier = Modifier.fillMaxWidth()
                                ) { month = m }
                            }
                        }
                        // pad the last row so buttons keep equal widths
                        repeat(6 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // --- day ---
                PickerLabel("روز")
                Spacer(Modifier.height(7.dp))
                FlowDayGrid(
                    days = (1..maxDay).toList(),
                    selected = day,
                    onSelect = { day = it }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "این ماه ${Dates.fa(maxDay)} روز دارد",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPicked(Jalali.JDate(year, month, day).iso()) },
                shape = RoundedCornerShape(14.dp)
            ) { Text("تأیید") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun PickerLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 7-column day grid with the same chip styling as the rest of the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowDayGrid(days: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 7
    ) {
        days.forEach { d ->
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (d == selected) Purple
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(d) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    Dates.fa(d.toString()),
                    fontSize = 13.sp,
                    fontWeight = if (d == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (d == selected) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
