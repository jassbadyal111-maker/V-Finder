package com.vfinder.app

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Indigo = Color(0xFF4731FF)
private val Cyan = Color(0xFF12D7FF)
private val Navy = Color(0xFF10154A)
private val BgLight = Color(0xFFF6F7FB)
private val BgDark = Color(0xFF0A0E25)

data class PersonRecord(val fields: Map<String, String>) {
    val name: String
        get() = fields.entries.firstOrNull {
            it.key.equals("name", true) || it.key.contains("person", true)
        }?.value.orEmpty()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VFinderApp() }
    }
}

@Composable
fun VFinderApp() {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()

    var dark by remember { mutableStateOf(false) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("No file selected") }
    var query by remember { mutableStateOf(TextFieldValue()) }
    var loading by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var searched by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        fileUri = uri
        fileName = displayName(resolver, uri)
        rows = emptyList()
        searched = false
        errorMessage = null
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Indigo) else lightColorScheme(primary = Indigo)) {
        if (loading) {
            LoadingScreen()
        } else {
            Scaffold(containerColor = if (dark) BgDark else BgLight) { pad ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(pad)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { TopBar(dark) { dark = !dark } }
                    item { BrandHeader() }
                    item {
                        FilePickerCard(fileName) {
                            launcher.launch(arrayOf("text/*", "text/csv", "application/json"))
                        }
                    }
                    item {
                        SearchPanel(
                            value = query,
                            onValue = { query = it },
                            enabled = fileUri != null
                        ) {
                            val uri = fileUri ?: return@SearchPanel
                            val search = query.text.trim()
                            if (search.isBlank()) {
                                errorMessage = "Enter a person name before searching."
                                return@SearchPanel
                            }
                            scope.launch {
                                loading = true
                                errorMessage = null
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        resolver.openInputStream(uri)?.use { input ->
                                            parseInputStream(input, fileName, search)
                                        } ?: error("Unable to open the selected file.")
                                    }
                                }
                                result.onSuccess {
                                    rows = it
                                    searched = true
                                }.onFailure {
                                    rows = emptyList()
                                    searched = true
                                    errorMessage = it.message ?: "Could not read the selected file."
                                }
                                loading = false
                            }
                        }
                    }
                    item {
                        AnimatedVisibility(visible = errorMessage != null) {
                            ErrorState(errorMessage.orEmpty())
                        }
                    }
                    item {
                        AnimatedVisibility(visible = searched && errorMessage == null) {
                            ResultSummary(rows.size, query.text)
                        }
                    }
                    items(rows) { PersonCard(it) }
                    if (searched && rows.isEmpty() && errorMessage == null) {
                        item { EmptyState(query.text) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun displayName(resolver: ContentResolver, uri: Uri): String {
    var cursor: Cursor? = null
    return try {
        cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor?.moveToFirst() == true) {
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else {
            uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
        }
    } catch (_: Exception) {
        uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
    } finally {
        cursor?.close()
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        LogoMark()
        Spacer(Modifier.width(12.dp))
        Column {
            Text("V-Finder", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("Find Anyone. Anywhere. Instantly.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LogoMark() {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Navy, Indigo)))
    ) {
        Icon(Icons.Default.Search, null, tint = Cyan, modifier = Modifier.align(Alignment.Center).size(34.dp))
        Box(Modifier.size(10.dp).background(Color.White, CircleShape).align(Alignment.TopEnd))
    }
}

@Composable
private fun TopBar(dark: Boolean, toggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Person Data Finder", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = toggle) {
            Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "Theme")
        }
    }
}

@Composable
private fun FilePickerCard(fileName: String, onPick: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFEDEBFF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Description, null, tint = Indigo)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(fileName, fontWeight = FontWeight.SemiBold)
                    Text("CSV • TSV • TXT • JSON", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onPick, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Indigo)) {
                    Text("SELECT FILE")
                }
            }
        }
    }
}

@Composable
private fun SearchPanel(
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    enabled: Boolean,
    onSearch: () -> Unit
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Enter person name") }
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSearch,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text("SEARCH", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ResultSummary(count: Int, query: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$count match(es) found", color = Color(0xFF16894F), fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(query, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun PersonCard(record: PersonRecord) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Indigo, Cyan))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(record.name.ifBlank { "Matching record" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Complete Information", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            record.fields.entries.take(8).forEach { (key, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(key.replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(0.35f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(value, modifier = Modifier.weight(0.65f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(query: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Tune, null, tint = Indigo, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(8.dp))
            Text("No match found", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("No record matched “$query”. Try a different spelling.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, Color(0xFF29146C)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark()
            Spacer(Modifier.height(18.dp))
            Text("V-Finder", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text("Find Anyone. Anywhere. Instantly.", color = Color.White.copy(alpha = .8f))
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(color = Cyan)
            Spacer(Modifier.height(10.dp))
            Text("Searching…", color = Color.White.copy(alpha = .85f))
        }
    }
}
