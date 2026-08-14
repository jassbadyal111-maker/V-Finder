package com.vfinder.app

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val VIndigo = Color(0xFF5846F2)
private val VCyan = Color(0xFF19D9FF)
private val VLightBackground = Color(0xFFF5F7FC)
private val VDarkBackground = Color(0xFF090D18)
private val VDarkSurface = Color(0xFF111827)
private val VDarkSurfaceVariant = Color(0xFF1B2435)

private val VLightColors = lightColorScheme(
    primary = VIndigo,
    onPrimary = Color.White,
    background = VLightBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECF5),
    onBackground = Color(0xFF121622),
    onSurface = Color(0xFF121622),
    onSurfaceVariant = Color(0xFF626A7A)
)

private val VDarkColors = darkColorScheme(
    primary = Color(0xFF9B91FF),
    onPrimary = Color(0xFF1B1745),
    background = VDarkBackground,
    surface = VDarkSurface,
    surfaceVariant = VDarkSurfaceVariant,
    onBackground = Color(0xFFF3F5FA),
    onSurface = Color(0xFFF3F5FA),
    onSurfaceVariant = Color(0xFFAEB7C8)
)

data class PersonRecord(val fields: Map<String, String>) {
    val name: String
        get() = fields.entries.firstOrNull { (key, value) ->
            val normalized = key.lowercase(Locale.ROOT).replace("_", " ").replace("-", " ").trim()
            value.isNotBlank() && (normalized == "name" || normalized == "full name" || normalized == "person name" || normalized.contains("person name") || normalized.endsWith(" name"))
        }?.value?.trim().orEmpty()
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
    val keyboard = LocalSoftwareKeyboardController.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val systemDark = isSystemInDarkTheme()
    var dark by rememberSaveable { mutableStateOf(systemDark) }
    var fileName by rememberSaveable { mutableStateOf("No file selected") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var allRows by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var rows by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var searched by rememberSaveable { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var loadingFile by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        fileUri = uri
        fileName = displayName(resolver, uri)
        allRows = emptyList()
        rows = emptyList()
        searched = false
        errorMessage = null
        loadingFile = true
        runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
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
            .mapNotNull { it.name.takeIf(String::isNotBlank) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .filter { it.contains(q, ignoreCase = true) }
            .sortedWith(compareBy<String>({ !it.startsWith(q, ignoreCase = true) }, { it.length }, { it.lowercase(Locale.ROOT) }))
            .take(8)
            .toList()
    }

    fun performSearch() {
        val q = query.trim()
        keyboard?.hide()
        if (q.isBlank()) {
            errorMessage = "Enter a person name before searching."
            return
        }
        if (allRows.isEmpty()) {
            errorMessage = "Add a data file from the side menu first."
            return
        }
        searching = true
        searched = true
        errorMessage = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                allRows.filter { record -> record.fields.values.any { value -> value.contains(q, ignoreCase = true) } }
            }
            rows = result
            searching = false
        }
    }

    MaterialTheme(colorScheme = if (dark) VDarkColors else VLightColors) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
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
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { TopBar(dark, onMenu = { scope.launch { drawerState.open() } }, toggle = { dark = !dark }) }
                    item { BrandHeader() }
                    item {
                        SearchPanel(
                            query = query,
                            onQuery = { query = it; searched = false; errorMessage = null },
                            suggestions = suggestions,
                            enabled = allRows.isNotEmpty() && !loadingFile,
                            searching = searching,
                            onSuggestion = { selected -> query = selected; searched = false; errorMessage = null },
                            onSearch = ::performSearch
                        )
                    }
                    item { if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape)) }
                    item { if (loadingFile) LoadingFileBanner() }
                    item { errorMessage?.let { ErrorState(it) } }
                    if (searched && errorMessage == null) {
                        item { ResultSummary(rows.size, query) }
                        items(rows, key = { it.fields.entries.joinToString("|") }) { PersonCard(it) }
                        if (rows.isEmpty()) item { EmptyState(query) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(fileName: String, loading: Boolean, onPickFile: () -> Unit, onClose: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(Modifier.size(50.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("V-Finder", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Data sources", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close menu") }
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        NavigationDrawerItem(label = { Text("Add data file", fontWeight = FontWeight.SemiBold) }, selected = false, icon = { Icon(Icons.Default.FolderOpen, null) }, onClick = onPickFile)
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("CURRENT DATA SOURCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(fileName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (loading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (fileName != "No file selected") {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onClear) { Text("Remove file") }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("CSV • TSV • TXT • JSON", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun displayName(resolver: ContentResolver, uri: Uri): String {
    var cursor: Cursor? = null
    return try {
        cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor?.moveToFirst() == true) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
    } catch (_: Exception) {
        uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
    } finally {
        cursor?.close()
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LogoMark(Modifier.size(64.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("V-Finder", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            Text("Search your people data instantly", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LogoMark(modifier: Modifier = Modifier) {
    Icon(painterResource(com.vfinder.app.R.drawable.ic_vfinder_logo), contentDescription = "V-Finder", modifier = modifier)
}

@Composable
private fun TopBar(dark: Boolean, onMenu: () -> Unit, toggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Open side menu") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(Modifier.size(28.dp))
            Spacer(Modifier.width(7.dp))
            Text("V-Finder", fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = toggle) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme") }
    }
}

@Composable
private fun SearchPanel(query: String, onQuery: (String) -> Unit, suggestions: List<String>, enabled: Boolean, searching: Boolean, onSuggestion: (String) -> Unit, onSearch: () -> Unit) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Find a person", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Type a name to see matching suggestions", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), enabled = enabled && !searching, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("e.g. Jass, Aman, Simran") }, label = { Text("Person name") }, shape = RoundedCornerShape(18.dp))
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        suggestions.forEach { suggestion ->
                            Row(Modifier.fillMaxWidth().clickable { onSuggestion(suggestion) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(suggestion, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Icon(Icons.Default.Search, "Use suggestion", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSearch, enabled = enabled && query.isNotBlank() && !searching, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                if (searching) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Search, null)
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
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Loading data and preparing suggestions…", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ResultSummary(count: Int, query: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$count result${if (count == 1) "" else "s"}", color = Color(0xFF16894F), fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("for “$query”", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun PersonCard(record: PersonRecord) {
    val name = record.name.ifBlank { "Matching record" }
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "VF" }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(VIndigo, VCyan))), contentAlignment = Alignment.Center) {
                    Text(initials, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("PERSON INFORMATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(15.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(7.dp))
            record.fields.entries.take(10).forEachIndexed { index, (key, value) ->
                if (value.isNotBlank()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                        Text(key.replace("_", " ").replace("-", " ").replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(.38f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(value, modifier = Modifier.weight(.62f), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = if (index == 0) FontWeight.Medium else FontWeight.Normal, maxLines = 4, overflow = TextOverflow.Ellipsis)
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
            Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(8.dp))
            Text("No match found", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("No record matched “$query”. Try another spelling.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(14.dp), fontSize = 13.sp)
    }
}
