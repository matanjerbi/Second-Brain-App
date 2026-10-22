@file:OptIn(ExperimentalMaterial3Api::class)

package com.matan.secondbrain.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.matan.secondbrain.data.Repository
import com.matan.secondbrain.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(repo: Repository, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var images by remember { mutableStateOf(listOf<Uri>()) }
    var saving by remember { mutableStateOf(false) }
    val voice = rememberVoiceInput { spoken -> text = if (text.isBlank()) spoken else "$text $spoken" }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) images = images + uris
    }

    Scaffold(containerColor = Palette.Ground) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
                Text("מה לזכור?", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("רעיון, קישור, רשימת קניות, מספר טלפון…") },
                minLines = 5,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = voice) { Icon(Icons.Filled.Mic, contentDescription = null); Text("  הכתבה") }
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Icon(Icons.Filled.Image, contentDescription = null); Text("  תמונה") }
            }
            if (images.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images) { uri ->
                        AsyncImage(
                            model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        val note = if (images.isNotEmpty()) text else ""
                        if (images.isEmpty() && text.isNotBlank()) repo.saveText(text)
                        images.forEach { repo.saveImage(it, note) }
                        Toast.makeText(context, "נשמר בזיכרון", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                },
                enabled = !saving && (text.isNotBlank() || images.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Lime, contentColor = Palette.Ground),
            ) { Text("שמור") }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Palette.Surface,
    unfocusedContainerColor = Palette.Surface,
    focusedBorderColor = Palette.Lime,
    unfocusedBorderColor = Palette.Line,
    cursorColor = Palette.Lime,
)
