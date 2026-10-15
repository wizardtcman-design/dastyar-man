package com.dastyar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ui.theme.Purple

/**
 * The one message composer shared by every chat in the app: the general
 * assistant, the three advisors, and the lunch and dinner chats. Keeping a
 * single implementation is what guarantees the send action sits in the same row
 * as the input field everywhere and behaves identically.
 *
 * Layout, deliberately: the input field and the send button live in ONE Row, so
 * the button is positioned against the field, not against whatever else the
 * composer contains. The model caption is placed UNDER that Row, so adding the
 * model chooser never moves the field away from its button. The Row aligns the
 * button to the field's bottom edge (`Alignment.Bottom`), which is what keeps
 * the button visually attached to the field while the field grows to several
 * lines, and keeps it in place when the keyboard opens or the model menu shows.
 */
@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    accent: Color,
    channel: String,
    sendEnabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder, fontSize = 13.sp) },
                shape = RoundedCornerShape(20.dp),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (sendEnabled) {
                            Brush.linearGradient(listOf(Purple, accent))
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    Color.Gray.copy(alpha = .35f),
                                    Color.Gray.copy(alpha = .35f)
                                )
                            )
                        }
                    )
                    .clickable(enabled = sendEnabled) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Send, contentDescription = "ارسال", tint = Color.White)
            }
        }

        // The model chooser sits under the whole row, so it can never push the
        // field out of line with its send button.
        ChatModelCaption(
            channel = channel,
            accent = accent,
            modifier = Modifier.padding(start = 6.dp, top = 3.dp)
        )
    }
}
