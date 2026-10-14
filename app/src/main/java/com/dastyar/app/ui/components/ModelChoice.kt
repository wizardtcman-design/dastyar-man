package com.dastyar.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ai.ServiceKeys

/**
 * The compact chat model chooser shown directly above a chat composer.
 *
 * It always shows which model the next message will use, so the user never has
 * to open Settings to know. When the active provider offers more than one model
 * the chip opens a short menu; picking one applies to this conversation only and
 * is remembered per channel, so changing it in one chat never affects another.
 * With a single-model provider the chip is a quiet, non-interactive label.
 */
@Composable
fun ChatModelBar(
    channel: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val provider = ServiceKeys.textProvider()
    val options = provider.pickerModels
    var version by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }

    // Re-reads after a choice; the provider is read fresh on every composition.
    val current = remember(version, provider.id) {
        ServiceKeys.conversationModel(channel).ifBlank { ServiceKeys.textModel() }
    }
    val label = options.firstOrNull { it.first == current }?.second ?: current
    val canChoose = options.size > 1

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "مدل:",
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = .12f),
                modifier = if (canChoose) Modifier.clickable { open = true } else Modifier
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (canChoose) Text("⌄", fontSize = 12.sp, color = accent)
                }
            }
            if (canChoose) {
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    options.forEach { (id, text) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (id == current) "✓ " else "") + text,
                                    fontSize = 12.5.sp
                                )
                            },
                            onClick = {
                                ServiceKeys.setConversationModel(ctx, channel, id)
                                version++
                                open = false
                            }
                        )
                    }
                }
            }
        }
    }
}
