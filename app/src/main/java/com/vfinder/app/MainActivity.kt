package com.vfinder.app

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

data class PersonRecord(val fields: Map<String, String>)

private val NAME_KEYS = setOf("name", "full name", "person name", "customer name", "member name")
private val Indigo = Color(0xFF5846F2)
private val Cyan = Color(0xFF19CFF2)
private val LightBg = Color(0xFFF5F7FC)
private val DarkBg = Color(0xFF090D18)

private fun normalizedKey(value: String): String = value.lowercase(Locale.ROOT)
    .replace('_', ' ').replace('-', ' ').trim().replace(Regex("\\s+"), " ")

private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

private fun personName(record: PersonRecord): String = record.fields.entries
    .firstOrNull { it.value.isNotBlank() && normalizedKey(it.key) in NAME_KEYS }
    ?.value?.trim().orEmpty()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VFinderApp() }
    }
}

@Composable
private fun VFinderApp() {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var dark by rememberSaveable { mutableStateOf(isSystemInDarkTheme()) }
    var query by rememberSaveable { mutableStateOf("") }
    var fileName by rememberSaveable { mutableStateOf("No file selected") }
    var records by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var results by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var searched by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        fileName = displayName(context, uri)
        val selectedName = fileName
        loading = true
        records = emptyList()
        results = emptyList()
        query = ""
        searched = false
        error = null
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.use { parseInputStream(it, selectedName) }
                        ?: error("Unable to open the selected file.")
                }
            }
            parsed.onSuccess {
                records = it
                loading = false
            }.onFailure {
                records = emptyList()
                loading = false
                error = it.message ?: "Unable to read the selected file."
            }
        }
    }

    val suggestions = remember(query, records) {
        val q = normalize(query)
        if (q.isBlank()) emptyList()
        else records.asSequence().map(::personName).filter(String::isNotBlank)
            .distinctBy(::normalize).filter { normalize(it).contains(q) }
            .sortedWith(compareBy<String>({ !normalize(it).startsWith(q) }, { it.length }, { normalize(it) }))
            .take(6).toList()
    }

    fun search() {
        keyboard?.hide()
        val q = normalize(query)
        when {
            q.isBlank() -> error = "Enter a person name."
            records.isEmpty() -> error = "Add a data file from the side menu first."
            else -> {
                error = null
                searched = true
                results = records.filter { normalize(personName(it)).contains(q) }
            }
        }
    }

    val light = lightColorScheme(primary = Indigo, background = LightBg, surface = Color.White,
        surfaceVariant = Color(0xFFE9ECF5), onBackground = Color(0xFF111522),
        onSurface = Color(0xFF111522), onSurfaceVariant = Color(0xFF626A7A))
    val darkColors = darkColorScheme(primary = Color(0xFFA49CFF), background = DarkBg,
        surface = Color(0xFF111827), surfaceVariant = Color(0xFF1B2435),
        onBackground = Color(0xFFF3F5FA), onSurface = Color(0xFFF3F5FA),
        onSurfaceVariant = Color(0xFFAEB7C8))

    MaterialTheme(colorScheme = if (dark) darkColors else light) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.fillMaxSize().padding(18.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Logo(Modifier.size(48.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("V-Finder", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Data source", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { scope.launch { drawer.close() } }) {
                                Icon(Icons.Default.Close, "Close")
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        NavigationDrawerItem(
                            label = { Text("Add data file", fontWeight = FontWeight.Bold) },
                            selected = false,
                            icon = { Icon(Icons.Default.FolderOpen, null) },
                            onClick = { picker.launch(arrayOf("text/*", "application/json", "text/csv")) }
                        )
                        Spacer(Modifier.height(12.dp))
                        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("CURRENT FILE", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(fileName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (loading) {
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                                if (records.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("${records.size} records loaded", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = {
                                        records = emptyList(); results = emptyList(); fileName = "No file selected"
                                        query = ""; searched = false; error = null
                                    }) { Text("Remove file") }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { TopBar(dark, { scope.launch { drawer.open() } }, { dark = !dark }) }
                    item { BrandHeader() }
                    item {
                        SearchPanel(query, suggestions, records.isNotEmpty() && !loading,
                            { query = it; searched = false; error = null },
                            { query = it; searched = false; error = null }, ::search)
                    }
                    error?.let { message -> item { MessageCard(message) } }
                    if (searched && error == null) {
                        item { Text("${results.size} result${if (results.size == 1) "" else "s"}",
                            color = Color(0xFF218A5A), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        items(results, key = { it.fields.entries.joinToString("\u0001") }) { PersonCard(it) }
                        if (results.isEmpty()) item { EmptyCard(query) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(dark: Boolean, menu: () -> Unit, theme: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        IconButton(onClick = menu) { Icon(Icons.Default.Menu, "Open menu") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(Modifier.size(30.dp)); Spacer(Modifier.width(7.dp)); Text("V-Finder", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        IconButton(onClick = theme) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Theme") }
    }
}

@Composable
private fun BrandHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Logo(Modifier.size(74.dp)); Spacer(Modifier.width(14.dp))
        Column {
            Text("V-Finder", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("Search your people data instantly", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Logo(modifier: Modifier) {
    Icon(painterResource(R.drawable.ic_vfinder_logo), "V-Finder logo", modifier)
}

@Composable
private fun SearchPanel(query: String, suggestions: List<String>, enabled: Boolean,
    onQuery: (String) -> Unit, choose: (String) -> Unit, search: () -> Unit) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Find a person", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("Type a name to see matching suggestions", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = query, onValueChange = onQuery, enabled = enabled, singleLine = true,
                modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Person name") }, placeholder = { Text("e.g. Aman Kumar") },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear") } },
                shape = RoundedCornerShape(18.dp))
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        suggestions.forEach { name ->
                            Row(Modifier.fillMaxWidth().clickable { choose(name) }.padding(11.dp), Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Indigo, Cyan))), Alignment.Center) {
                                    Text(initials(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Spacer(Modifier.width(10.dp)); Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = search, enabled = enabled && query.isNotBlank(), Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Indigo)) {
                Icon(Icons.Default.Search, null); Spacer(Modifier.width(9.dp)); Text("SEARCH", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun PersonCard(record: PersonRecord) {
    val name = personName(record).ifBlank { "Unnamed person" }
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Indigo, Cyan))), Alignment.Center) {
                    Text(initials(name), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("PERSON INFORMATION", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp)); HorizontalDivider()
            record.fields.entries.filter { it.value.isNotBlank() && normalizedKey(it.key) !in NAME_KEYS }
                .take(8).forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.Top) {
                        Text(pretty(entry.key), Modifier.width(120.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(entry.value, Modifier.weight(1f), fontSize = 14.sp)
                    }
                }
        }
    }
}

@Composable
private fun MessageCard(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun EmptyCard(query: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Person, null, Modifier.size(32.dp)); Spacer(Modifier.height(8.dp))
            Text("No person found", fontWeight = FontWeight.Bold)
            Text("No Name field matched \"$query\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun initials(name: String): String = name.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    .take(2).joinToString("") { it.first().uppercase() }

private fun pretty(key: String): String = key.replace('_', ' ').replace('-', ' ').trim()
    .split(Regex("\\s+")).joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
