@file:OptIn(ExperimentalMaterial3Api::class)

package com.matan.secondbrain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matan.secondbrain.data.Repository
import com.matan.secondbrain.ui.theme.Palette

@Composable
fun ResultsScreen(repo: Repository, initialQuery: String, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    var field by remember { mutableStateOf(initialQuery) }
    var query by remember { mutableStateOf(initialQuery) }
    val hasKey by repo.settings.hasKey.collectAsStateWithLifecycle()
    val indexVersion by repo.indexVersion.collectAsStateWithLifecycle()
    val isTag = query.startsWith("#")

    var answer by remember { mutableStateOf<Repository.Answer?>(null) }
    var thinking by remember { mutableStateOf(false) }

    LaunchedEffect(query, hasKey) {
        answer = null
        if (hasKey && !isTag && query.isNotBlank()) {
            thinking = true
            answer = repo.ask(query)
            thinking = false
        }
    }

    val expanded = answer?.expanded.orEmpty()
    val local = remember(query, indexVersion, expanded) { repo.search(query, expanded) }
    val sourceIds = answer?.sourceIds.orEmpty()
    val ordered = remember(local, sourceIds) {
        val byId = local.associateBy { it.id }
        val first = sourceIds.mapNotNull { byId[it] }
        first + local.filter { it.id !in sourceIds }
    }
    val voice = rememberVoiceInput { field = it; query = it }

    Scaffold(containerColor = Palette.Ground) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
                    TextField(
                        value = field,
                        onValueChange = { field = it },
                        modifier = Modifier.weight(1f).border(1.dp, Palette.Lime, RoundedCornerShape(14.dp)),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = { IconButton(onClick = voice) { Icon(Icons.Filled.Mic, contentDescription = "חיפוש קולי", tint = Palette.Lime) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (field.isNotBlank()) query = field.trim() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Palette.Surface,
                            unfocusedContainerColor = Palette.Surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Palette.Lime,
                        ),
                    )
                }
            }

            if (hasKey && !isTag) {
                item { AnswerCard(thinking = thinking, answer = answer) }
            }

            item {
                Text(
                    when {
                        isTag -> "${ordered.size} פריטים בתגית ${query.removePrefix("#")}"
                        ordered.isEmpty() && !thinking -> "לא נמצאו פריטים"
                        else -> "נמצא לפי משמעות · ${ordered.size}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(ordered, key = { it.id }) { item ->
                ItemCard(item, onClick = { onOpen(item.id) }, highlight = item.id in sourceIds.take(3))
            }
        }
    }
}

@Composable
private fun AnswerCard(thinking: Boolean, answer: Repository.Answer?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.LimeDeep)
            .border(1.dp, Color(0xFF3A4A22), RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Palette.Lime, modifier = Modifier.size(16.dp))
            Text("תשובה", style = MaterialTheme.typography.labelMedium, color = Palette.Lime)
        }
        when {
            thinking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Lime)
                Text("מחפש בזיכרון שלך…", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
            answer?.error != null -> Text(answer.error, style = MaterialTheme.typography.bodyMedium, color = Palette.Red)
            answer?.text != null -> SelectionContainer { Text(answer.text, style = MaterialTheme.typography.bodyLarge) }
            else -> Text("אין תשובה כרגע.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        }
    }
}
