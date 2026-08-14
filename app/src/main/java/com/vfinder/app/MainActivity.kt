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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DrawerValue
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
            it.key.equals("name", true) || it.key.contains("person", true) || it.key.contains("full_name", true)
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var dark by remember { mutableStateOf(false) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("No file selected") }
    var query by remember { mutableStateOf("") }
    var allRows by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var rows by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var searched by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var loadingFile by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        fileUri = uri
        fileName = displayName(resolver, uri)
        rows = emptyList()
        allRows = emptyList()
        searched = false
        errorMessage = null
        loadingFile = true
        runCatching {
            resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.use { input -> parseInputStream(input, fileName, "") }
                        ?: error("Unable to open the selected file.")
                }
            }
            result.onSuccess {
                allRows = it
                loadingFile = false
                query = ""
                scope.launch { drawerState.close() }
            }.onFailure {
                allRows = emptyList()
                loadingFile = false
                errorMessage = it.message ?: "Could not read the selected file."
            }
        }
    }

    val suggestions = remember(query, allRows) {
        val q = query.trim()
        if (q.isBlank()) emptyList()
        else allRows.asSequence()
            .mapNotNull { it.name.trim().takeIf(String::isNotBlank) }
            .distinctBy { it.lowercase() }
            .filter { it.contains(q, ignoreCase = true) }
            .sortedWith(compareBy<String>({ !it.startsWith(q, ignoreCase = true) }, { it.length }, { it.lowercase() }))
            .take(6)
            .toList()
    }

    fun performSearch() {
        val q = query.trim()
        if (q.isBlank()) {
            errorMessage = "Enter a person name before searching."
            return
        }
        if (allRows.isEmpty()) {
            errorMessage = "Add a data file from the side menu first."
            return
        }
        searching = true
        errorMessage = null
        searched = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                allRows.filter { record ->
                    record.fields.values.any { value -> value.contains(q, ignoreCase = true) }
                }
            }
            rows = result
            searching = false
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Indigo) else lightColorScheme(primary = Indigo)) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
                        fileName = fileName,
                        loading = loadingFile,
                        onPickFile = { launcher.launch(arrayOf("text/*", "text/csv", "application/json")) },
                        onClose = { scope.launch { drawerState.close() } },
                        onClear = {
                            fileUri = null
                            fileName = "No file selected"
                            allRows = emptyList()
                            rows = emptyList()
                            query = ""
                            searched = false
                            errorMessage = null
                        }
                    )
                }
            }
        ) {
            Scaffold(containerColor = if (dark) BgDark else BgLight) { pad ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { TopBar(dark, onMenu = { scope.launch { drawerState.open() } }, toggle = { dark = !dark }) }
                    item { BrandHeader() }
                    item {
                        SearchPanel(
                            query = query,
                            onQuery = {
                                query = it
                                if (searched) searched = false
                                errorMessage = null
                            },
                            suggestions = suggestions,
                            enabled = allRows.isNotEmpty(),
                            searching = searching,
                            onSuggestion = { selected ->
                                query = selected
                                suggestions.toList()
                            },
                            onSearch = ::performSearch
                        )
                    }
                    item {
                        if (searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape))
                    }
                    item {
                        if (loadingFile) LoadingFileBanner()
                    }
                    item {
                        errorMessage?.let { ErrorState(it) }
                    }
                    if (searched && errorMessage == null) {
                        item { ResultSummary(rows.size, query) }
                        items(rows, key = { it.fields.hashCode() }) { PersonCard(it) }
                        if (rows.isEmpty()) item { EmptyState(query) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    fileName: String,
    loading: Boolean,
    onPickFile: () -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("V-Finder", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Data sources", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close menu") }
        }
        Spacer(Modifier.height(20.dp))
        Divider()
        Spacer(Modifier.height(12.dp))
        NavigationDrawerItem(
            label = { Text("Add data file", fontWeight = FontWeight.SemiBold) },
            selected = false,
            icon = { Icon(Icons.Default.FolderOpen, null) },
            onClick = onPickFile
        )
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("Current file", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = Indigo)
                    Spacer(Modifier.width(10.dp))
                    Text(fileName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                if (loading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (fileName != "No file selected") {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onClear, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Indigo)) {
                        Text("REMOVE FILE")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Supported: CSV, TSV, TXT and JSON", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun displayName(resolver: ContentResolver, uri: Uri): String {
    var cursor: Cursor? = null
    return try {
        cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor?.moveToFirst() == true) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        else uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
    } catch (_: Exception) {
        uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
    } finally {
        cursor?.close()
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        LogoMark(Modifier.size(62.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("V-Finder", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("Search your people data instantly", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LogoMark(modifier: Modifier = Modifier) {
    Icon(painterResource(com.vfinder.app.R.drawable.ic_vfinder_logo), contentDescription = "V-Finder", modifier = modifier)
}

@Composable
private fun TopBar(dark: Boolean, onMenu: () -> Unit, toggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Open side menu") }
        Text("Person Data Finder", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = toggle) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Theme") }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    onQuery: (String) -> Unit,
    suggestions: List<String>,
    enabled: Boolean,
    searching: Boolean,
    onSuggestion: (String) -> Unit,
    onSearch: () -> Unit
) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Find a person", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Start typing a name for live suggestions", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && !searching,
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("e.g. Jass, Aman, Simran") },
                label = { Text("Person name") },
                shape = RoundedCornerShape(18.dp)
            )
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        suggestions.forEach { suggestion ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null, tint = Indigo, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(suggestion, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                IconButton(onClick = { onSuggestion(suggestion) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Search, "Use suggestion", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSearch, enabled = enabled && query.isNotBlank() && !searching, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Indigo)) {
                if (searching) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text(if (searching) "SEARCHING…" else "SEARCH", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LoadingFileBanner() {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Indigo)
            Spacer(Modifier.width(12.dp))
            Text("Loading file and preparing suggestions…", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ResultSummary(count: Int, query: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$count match(es)", color = Color(0xFF16894F), fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("for “$query”", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun PersonCard(record: PersonRecord) {
    val displayName = record.name.ifBlank { record.fields.values.firstOrNull().orEmpty().ifBlank { "Matching record" } }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Indigo, Cyan))), contentAlignment = Alignment.Center) {
                    Text(displayName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(displayName, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Person information", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            record.fields.entries.take(10).forEach { (key, value) ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(key.replace('_', ' ').replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(.38f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, modifier = Modifier.weight(.62f), fontSize = 13.sp)
                    }
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
            Text("No record matched “$query”. Try another spelling or suggestion.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
