package com.dastyar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ai.AiClient
import com.dastyar.app.ai.Prompts
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import kotlinx.coroutines.launch

private val mealTypes = listOf("صبحانه", "ناهار", "شام", "میان‌وعده")

@Composable
fun CookingScreen(vm: MainViewModel) {
    val scope = rememberCoroutineScope()
    var ingredients by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf("ناهار") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        ScreenHeader(
            emoji = "🍳",
            title = "آشپزی هوشمند",
            subtitle = "بگو خونه چی داری، دستیار چند غذا پیشنهاد می‌ده."
        )

        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = ingredients,
            onValueChange = { ingredients = it },
            label = { Text("مواد موجود در خانه") },
            placeholder = { Text("مثلاً: سیب، تخم‌مرغ و آرد دارم.") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            minLines = 3
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("دسته‌بندی", "🍽")
        Spacer(Modifier.height(10.dp))
        ChoiceChips(mealTypes, meal) { meal = it }

        Spacer(Modifier.height(16.dp))
        GradientButton("پیشنهاد غذا 🍽", enabled = !loading && ingredients.isNotBlank()) {
            if (loading) return@GradientButton
            loading = true
            error = null
            result = null
            scope.launch {
                val res = AiClient.chat(
                    system = Prompts.base(),
                    history = emptyList(),
                    userMessage = Prompts.cookingPrompt(ingredients, meal)
                )
                loading = false
                res.onSuccess { result = it }
                    .onFailure { error = it.message ?: "خطا در دریافت پیشنهاد" }
            }
        }

        if (loading) {
            Spacer(Modifier.height(18.dp))
            Row {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("در حال فکر کردن…", fontSize = 13.sp)
            }
        }

        error?.let {
            Spacer(Modifier.height(14.dp))
            DastyarCard(accent = MaterialTheme.colorScheme.error) {
                Text("⚠️ $it", fontSize = 13.sp)
            }
        }

        result?.let { text ->
            Spacer(Modifier.height(18.dp))
            RecipeCards(text)
        }

        Spacer(Modifier.height(30.dp))
    }
}

/** Renders the AI's structured recipe answer as separate cards per dish. */
@Composable
private fun RecipeCards(text: String) {
    val blocks = text.split("🍽")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (blocks.isEmpty()) {
        DastyarCard(accent = MaterialTheme.colorScheme.primary) {
            Text(text, fontSize = 14.sp)
        }
        return
    }

    blocks.forEach { block ->
        val lines = block.lines().filter { it.isNotBlank() }
        val title = lines.firstOrNull()?.trim().orEmpty()
        DastyarCard(accent = MaterialTheme.colorScheme.primary) {
            Text("🍽 $title", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            lines.drop(1).forEach { line ->
                Text(
                    line.trim(),
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
