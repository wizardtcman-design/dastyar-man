package com.dastyar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ai.AiClient
import com.dastyar.app.ai.Prompts
import com.dastyar.app.data.SavedRecipe
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Green
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.ui.theme.Rose
import kotlinx.coroutines.launch

/**
 * The cooking tab. Three ways to get real, home-cookable Iranian food:
 * a lunch/dinner suggestion, a "what should I cook today?" action, and a
 * suggestion built from whatever the user has at home. Every result is a
 * structured recipe the user can read and save.
 *
 * This screen only reads from the existing AI client ([AiClient.chat]); it adds
 * no provider, key or endpoint of its own.
 */
@Composable
fun CookingScreen(vm: MainViewModel) {
    val scope = rememberCoroutineScope()
    val saved by vm.savedRecipes.collectAsState()

    var servings by remember { mutableIntStateOf(4) }
    var filters by remember { mutableStateOf(setOf<String>()) }

    // Shared loading/error state, so no two requests can run at once.
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Each slot keeps its own dishes, so a new request replaces only that slot.
    var lunch by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var dinner by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var pantryDishes by remember { mutableStateOf<List<Recipe>>(emptyList()) }

    var pantryInput by remember { mutableStateOf("") }

    suspend fun ask(prompt: String): Result<List<Recipe>> =
        AiClient.chat(
            system = Prompts.base(),
            history = emptyList(),
            userMessage = prompt
        ).map { parseRecipes(it) }

    fun run(block: suspend () -> Result<List<Recipe>>, onOk: (List<Recipe>) -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            val res = block()
            busy = false
            res.onSuccess { if (it.isEmpty()) error = "پیشنهادی پیدا نشد؛ دوباره امتحان کن." else onOk(it) }
                .onFailure { error = it.message ?: "خطا در دریافت پیشنهاد" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        CookingHeader(filters = filters, onToggleFilter = { f ->
            filters = if (f in filters) filters - f else filters + f
        }, servings = servings, onServings = { servings = it })

        Spacer(Modifier.height(14.dp))

        // ---- lunch ----
        MealSection(
            emoji = "🍲",
            title = "پیشنهاد هوشمند ناهار",
            accent = Amber,
            dishes = lunch,
            busy = busy,
            onNew = { run({ ask(Prompts.mealSuggestPrompt("ناهار", servings, filters.toList())) }) { lunch = it } },
            onSave = { d -> vm.saveRecipe(d.name, "ناهار", d.raw) }
        )

        Spacer(Modifier.height(16.dp))

        // ---- dinner ----
        MealSection(
            emoji = "🌙",
            title = "پیشنهاد هوشمند شام",
            accent = Purple,
            dishes = dinner,
            busy = busy,
            onNew = { run({ ask(Prompts.mealSuggestPrompt("شام", servings, filters.toList())) }) { dinner = it } },
            onSave = { d -> vm.saveRecipe(d.name, "شام", d.raw) }
        )

        Spacer(Modifier.height(16.dp))

        // ---- what should I cook today ----
        GradientButton("🍽 امروز چی بپزم؟", enabled = !busy) {
            run({ ask(Prompts.todaySuggestPrompt(servings, filters.toList())) }) {
                lunch = it.take(1)
                dinner = it.drop(1)
            }
        }

        error?.let {
            Spacer(Modifier.height(14.dp))
            DastyarCard(accent = MaterialTheme.colorScheme.error) {
                Text("⚠️ $it", fontSize = 13.sp)
            }
        }

        if (busy) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("در حال پیدا کردن غذا برای تو…", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- pantry ----
        PantrySection(
            input = pantryInput,
            onInput = { pantryInput = it },
            busy = busy,
            dishes = pantryDishes,
            onSuggest = {
                val text = pantryInput.trim()
                if (text.isEmpty()) {
                    error = "اول مواد موجود در خانه را بنویس."
                } else {
                    run({ ask(Prompts.pantrySuggestPrompt(text, servings)) }) { pantryDishes = it }
                }
            },
            onSave = { d -> vm.saveRecipe(d.name, "خانگی", d.raw) }
        )

        // ---- saved recipes ----
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            SectionTitle("غذاهای ذخیره‌شده", "♡")
            Spacer(Modifier.height(10.dp))
            saved.forEach { r ->
                SavedRecipeCard(r, onDelete = { vm.deleteRecipe(r.id) })
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(34.dp))
    }
}

// ----------------------------------------------------------------- header

@Composable
private fun CookingHeader(
    filters: Set<String>,
    onToggleFilter: (String) -> Unit,
    servings: Int,
    onServings: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Amber, Rose)))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = .22f)),
                contentAlignment = Alignment.Center
            ) { Text("🍳", fontSize = 16.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("آشپزی", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Text(
                    "امروز چی بپزم؟",
                    color = Color.White.copy(alpha = .85f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // filters — one scrollable row, never wraps out of the screen
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("💰 اقتصادی", "⏱ سریع", "🏠 ساده و خانگی").forEach { f ->
                SelectChip(label = f, selected = f in filters, accent = Green, compact = true) {
                    onToggleFilter(f)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // servings — compact chips, horizontally scrollable
        Text("تعداد نفرات", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(1, 2, 3, 4, 5, 6).forEach { n ->
                SelectChip(
                    label = if (n == 6) "۶+" else com.dastyar.app.data.Dates.fa(n),
                    selected = servings == n,
                    accent = Cyan,
                    compact = true
                ) { onServings(n) }
            }
        }
    }
}

// ------------------------------------------------------------- meal block

@Composable
private fun MealSection(
    emoji: String,
    title: String,
    accent: Color,
    dishes: List<Recipe>,
    busy: Boolean,
    onNew: () -> Unit,
    onSave: (Recipe) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(title, emoji)
    }
    Spacer(Modifier.height(10.dp))

    if (dishes.isEmpty()) {
        DastyarCard(accent = accent) {
            Text(
                "برای گرفتن پیشنهاد، دکمهٔ «پیشنهاد جدید» را بزن.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onNew,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("پیشنهاد جدید")
            }
        }
    } else {
        dishes.forEach { d ->
            RecipeCard(d, accent = accent, busy = busy, onNew = onNew, onSave = { onSave(d) })
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(
            onClick = onNew,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("🔄 پیشنهاد جدید")
        }
    }
}

// --------------------------------------------------------------- pantry

@Composable
private fun PantrySection(
    input: String,
    onInput: (String) -> Unit,
    busy: Boolean,
    dishes: List<Recipe>,
    onSuggest: () -> Unit,
    onSave: (Recipe) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("با چیزهایی که در خانه دارم", "🧺")
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = input,
        onValueChange = onInput,
        placeholder = { Text("مثلاً: سیب‌زمینی، تخم‌مرغ، پیاز، گوجه و کمی گوشت", fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        minLines = 3,
        maxLines = 6,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Green,
            unfocusedBorderColor = Green.copy(alpha = .35f)
        )
    )
    Spacer(Modifier.height(10.dp))
    GradientButton("🍳 پیشنهاد غذا", enabled = !busy) { onSuggest() }

    if (dishes.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        dishes.forEach { d ->
            RecipeCard(d, accent = Green, busy = busy, onNew = onSuggest, onSave = { onSave(d) })
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ---------------------------------------------------------------- cards

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeCard(
    r: Recipe,
    accent: Color,
    busy: Boolean,
    onNew: () -> Unit,
    onSave: () -> Unit
) {
    DastyarCard(accent = accent) {
        Text(r.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)

        // quick facts, as wrapping pills
        FlowRow(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (r.servings.isNotBlank()) FactPill("👥", r.servings)
            if (r.prep.isNotBlank()) FactPill("⏱", "آماده‌سازی ${r.prep}")
            if (r.cook.isNotBlank()) FactPill("🔥", "پخت ${r.cook}")
        }

        if (r.ingredients.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("🧺 مواد لازم", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Spacer(Modifier.height(6.dp))
            r.ingredients.forEach { (name, amount) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("• $name", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (amount.isNotBlank()) {
                        Text(amount, fontSize = 13.sp, color = accent, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (r.steps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("👩‍🍳 طرز تهیه", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Spacer(Modifier.height(6.dp))
            r.steps.forEachIndexed { i, s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("${com.dastyar.app.data.Dates.fa(i + 1)}.", fontSize = 13.sp, color = accent)
                    Spacer(Modifier.width(6.dp))
                    Text(s, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        r.shortage?.let {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Amber.copy(alpha = .12f))
                    .border(1.dp, Amber.copy(alpha = .35f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text("🧂 کمبود: $it", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onNew,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("پیشنهاد جدید", fontSize = 12.5.sp)
            }
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("♡ ذخیره", fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
private fun FactPill(emoji: String, text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 11.sp)
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SavedRecipeCard(r: SavedRecipe, onDelete: () -> Unit) {
    val parsed = remember(r.body) { parseRecipes(r.body).firstOrNull() }
    DastyarCard(accent = Purple) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                Text(r.meal, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = .5f))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف", modifier = Modifier.size(17.dp))
            }
        }

        val d = parsed
        if (d != null) {
            if (d.ingredients.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "مواد: " + d.ingredients.joinToString("، ") { "${it.first} ${it.second}".trim() },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (d.steps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                d.steps.forEachIndexed { i, s ->
                    Text(
                        "${com.dastyar.app.data.Dates.fa(i + 1)}. $s",
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        } else if (r.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(r.body, fontSize = 12.5.sp)
        }
    }
}

// --------------------------------------------------------------- parsing

/** One parsed recipe block from the model's structured answer. */
private data class Recipe(
    val name: String,
    val servings: String = "",
    val prep: String = "",
    val cook: String = "",
    val ingredients: List<Pair<String, String>> = emptyList(),
    val steps: List<String> = emptyList(),
    val shortage: String? = null,
    val raw: String = ""
)

/**
 * Parses the marker-based answer into recipe blocks. It is tolerant: anything
 * that does not match the expected keys is kept as-is in [Recipe.raw] so the
 * user still sees the model's text instead of an empty card.
 */
private fun parseRecipes(text: String): List<Recipe> {
    val blocks = text.split("###")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val out = mutableListOf<Recipe>()
    blocks.forEach { block ->
        var name = ""
        var servings = ""
        var prep = ""
        var cook = ""
        val ings = mutableListOf<Pair<String, String>>()
        val steps = mutableListOf<String>()
        var shortage: String? = null
        var section = ""   // "ing" while reading ingredients, "steps" while reading steps

        block.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("نام:") || line.startsWith("نام :") ->
                    name = line.substringAfter(":").trim()
                line.startsWith("نفرات:") -> servings = line.substringAfter(":").trim()
                line.startsWith("آماده‌سازی:") || line.startsWith("آماده سازی:") ->
                    prep = line.substringAfter(":").trim()
                line.startsWith("پخت:") -> cook = line.substringAfter(":").trim()
                line.startsWith("کمبود:") -> shortage = line.substringAfter(":").trim()
                line.startsWith("مواد:") -> section = "ing"
                line.startsWith("مراحل:") -> section = "steps"
                else -> {
                    val body = line.removePrefix("-").removePrefix("•").trim()
                    if (body.isNotEmpty()) {
                        when {
                            section == "ing" -> {
                                val sep = body.indexOfFirst { it == ':' || it == '：' }
                                if (sep > 0) {
                                    ings += body.substring(0, sep).trim() to
                                            body.substring(sep + 1).trim()
                                } else {
                                    ings += body to ""
                                }
                            }
                            section == "steps" -> {
                                val clean = body.replace(Regex("^[۰-۹0-9]+[.٫)]\\s*"), "").trim()
                                if (clean.isNotEmpty()) steps += clean
                            }
                            else -> {
                                // a leading dish line with no key, e.g. "🍽 قورمه سبزی"
                                if (name.isBlank()) name = body.removePrefix("🍽").trim()
                            }
                        }
                    }
                }
            }
        }

        if (name.isNotBlank() || ings.isNotEmpty() || steps.isNotEmpty()) {
            out += Recipe(
                name = name.ifBlank { "غذای پیشنهادی" },
                servings = servings,
                prep = prep,
                cook = cook,
                ingredients = ings,
                steps = steps,
                shortage = shortage,
                raw = block
            )
        }
    }
    return out
}
