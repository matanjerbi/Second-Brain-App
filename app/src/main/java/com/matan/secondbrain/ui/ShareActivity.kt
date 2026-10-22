package com.matan.secondbrain.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.matan.secondbrain.SecondBrainApp
import com.matan.secondbrain.data.Repository
import com.matan.secondbrain.ui.theme.Palette
import com.matan.secondbrain.ui.theme.SbpTheme
import kotlinx.coroutines.launch

/** The sheet that opens from Android's "Share" menu. */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = (application as SecondBrainApp).repo
        val shared = readShare(intent)
        if (shared.isEmpty()) {
            Toast.makeText(this, "אין מה לשמור", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            SbpTheme {
                ShareSheet(repo, shared, onClose = { finish() })
            }
        }
    }

    private fun readShare(intent: Intent): Shared {
        val type = intent.type.orEmpty()
        val text = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { s ->
                // Many apps send the page title as the subject; keep it only when the text doesn't already contain it.
                val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                s.isNotBlank() && !body.contains(s)
            },
            intent.getStringExtra(Intent.EXTRA_TEXT),
        ).joinToString("\n").trim()
        val streams: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> parcelableList(intent)
            else -> listOfNotNull(parcelable(intent))
        }
        return Shared(
            text = text,
            images = if (type.startsWith("image/")) streams else emptyList(),
            audio = if (type.startsWith("audio/")) streams.firstOrNull() else null,
        )
    }

    @Suppress("DEPRECATION")
    private fun parcelable(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun parcelableList(intent: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
}

data class Shared(val text: String, val images: List<Uri>, val audio: Uri?) {
    fun isEmpty() = text.isBlank() && images.isEmpty() && audio == null
}

@Composable
private fun ShareSheet(repo: Repository, shared: Shared, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!saving) onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Palette.Ground)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Line))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Lime), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.Ground)
                }
                Column {
                    Text("לשמור בזיכרון?", style = MaterialTheme.typography.titleMedium)
                    val what = when {
                        shared.images.size > 1 -> "${shared.images.size} תמונות"
                        shared.images.size == 1 -> "תמונה"
                        shared.audio != null -> "הקלטה"
                        else -> shared.text.lineSequence().firstOrNull().orEmpty()
                    }
                    Text(what, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (shared.images.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shared.images) { uri ->
                        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)))
                    }
                }
            } else if (shared.text.isNotBlank()) {
                Text(
                    shared.text, maxLines = 5, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface).padding(14.dp),
                )
            }

            OutlinedTextField(
                value = note, onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("הערה (לא חובה)") },
                placeholder = { Text("למשל: לנסות את הניוקי") },
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
            )

            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        when {
                            shared.images.isNotEmpty() -> shared.images.forEach { repo.saveImage(it, listOf(note, shared.text).filter { s -> s.isNotBlank() }.joinToString("\n")) }
                            shared.audio != null -> repo.saveAudio(shared.audio, listOf(note, shared.text).filter { s -> s.isNotBlank() }.joinToString("\n"))
                            else -> repo.saveText(shared.text, note)
                        }
                        Toast.makeText(repo.appContext, "נשמר בזיכרון", Toast.LENGTH_SHORT).show()
                        onClose()
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Lime, contentColor = Palette.Ground),
            ) { Text(if (saving) "שומר…" else "שמור") }
        }
    }
}
