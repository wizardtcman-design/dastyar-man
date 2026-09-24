package com.dastyar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.Profile
import com.dastyar.app.ui.components.*

/**
 * Lets the user edit the full onboarding questionnaire from Settings.
 * Reuses the same question sections so the two stay in sync.
 */
@Composable
fun QuestionnaireEditor(profile: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(profile) }
    val steps = listOf("شخصی", "پریود", "پوست", "بی‌رمقی")

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("ویرایش پرسشنامه", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onDismiss) { Text("بستن") }
        }

        Box(Modifier.padding(horizontal = 12.dp)) {
            SingleChoiceChips(
                options = steps,
                selected = steps[step],
                accent = com.dastyar.app.ui.theme.Purple
            ) { picked -> step = steps.indexOf(picked).coerceAtLeast(0) }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (step) {
                0 -> PersonalSection(draft) { draft = it }
                1 -> PeriodSection(draft) { draft = it }
                2 -> SkinSection(draft) { draft = it }
                else -> FatigueSection(draft) { draft = it }
            }
            Spacer(Modifier.height(20.dp))
        }

        Column(Modifier.padding(16.dp)) {
            GradientButton("ذخیره تغییرات ✅") { onSave(draft) }
        }
    }
}
