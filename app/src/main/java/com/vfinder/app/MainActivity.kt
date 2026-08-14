package com.vfinder.app

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
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

private val Indigo = Color(0xFF5846F2)
private val Cyan = Color(0xFF18CFF7)
private val LightBackground = Color(0xFFF5F7FC)
private val DarkBackground = Color(0xFF080C16)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    background = LightBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECF5),
    onBackground = Color(0xFF121622),
    onSurface = Color(0xFF121622),
    onSurfaceVariant = Color(0xFF667085)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA59CFF),
    onPrimary = Color(0xFF211B54),
    background = DarkBackground,
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1B2435),
    onBackground = Color(0xFFF3F5FA),
    onSurface = Color(0xFFF3F5FA),
    onSurfaceVariant = Color(0xFFB2B9C8)
)

data class PersonRecord(val fields: LinkedHashMap<String, String>) {
    val name: String
        get() = fields.entries.firstOrNull { isNameField(it.key) && it.value.isNotBlank() }?.value?.trim().orEmpty()
}

private fun isNameField(key: String): Boolean {
    val normalized = key.lowercase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim()
    return normalized == "name" || normalized == "full name" || normalized == "person name" || normalized == "personname"
}

private fun normalizeName(value: String): String =
    value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VFinderApp() }
    }
}

@Composable
private fun VFinderApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var darkTheme by rememberSaveable { mutableStateOf(isSystemInDarkTheme()) }
    var query by rememberSaveable { mutableStateOf("") }
    var fileName by rememberSaveable { mutableStateOf("No file selected") }
    var records by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var results by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var loading by remember { mutableStateOf(false) }
    var searched by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        fileName = displayName(context, uri)
        records = emptyList()
        results = emptyList()
        query = ""
        searched = false
        error = null
        loading = true
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { parseInputStream(it, fileName) }
                        ?: error("Unable to open the selected file.")
                }
            }
            parsed.onSuccess {
                records = it
                loading = false
                scope.launch { drawerState.close() }
            }.onFailure {
                records = emptyList()
                loading = false
                error = it.message ?: "Unable to read the selected file."
            }
        }
    }

    val suggestions = remember(query, records) {
        val q = normalizeName(query)
        if (q.isBlank()) emptyList()
        else records.asSequence()
            .map { it.name }
            .filter { it.isNotBlank() }
            .distinctBy { normalizeName(it) }
            .filter { normalizeName(it).contains(q) }
            .sortedWith(compareBy({ !normalizeName(it).startsWith(q) }, { normalizeName(it).length }, { normalizeName(it) }))
            .take(6)
            .toList()
    }

    fun search() {
        keyboard?.hide()
        val q = normalizeName(query)
        if (q.isBlank()) {
            error = "Enter a person name."
            return
        }
        if (records.isEmpty()) {
            error = "Add a data file from the side menu first."
            return
        }
        error = null
        searched = true
        results = records.filter { normalizeName(it.name).contains(q) }
    }

    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors) {
        ModalNavigationDrawer(
            drawerState = drawerState,
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
                            IconButton(onClick = { scope.launch { drawerState.close() } }) { Icon(Icons.Default.Close, "Close") }
                        }
                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        NavigationDrawerItem(
                            label = { Text("Add data file", fontWeight = FontWeight.Bold) },
                            selected = false,
                            icon = { Icon(Icons.Default.FolderOpen, null) },
                            onClick = { picker.launch(arrayOf("text/*", "application/json")) }
                        )
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("CURRENT FILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(fileName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (loading) {
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                                if (records.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("${records.size} records loaded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        ) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { TopBar(darkTheme, { scope.launch { drawerState.open() } }, { darkTheme = !darkTheme }) }
                    item { BrandHeader() }
                    item {
                        SearchPanel(
                            query = query,
                            onQuery = { query = it; searched = false; error = null },
                            suggestions = suggestions,
                            enabled = records.isNotEmpty() && !loading,
                            onSuggestion = { query = it; searched = false; error = null },
                            onSearch = ::search
                        )
                    }
                    item { error?.let { ErrorCard(it) } }
                    if (searched && error == null) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${results.size} result${if (results.size == 1) "" else "s"}", color = Color(0xFF218A5A), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("for “$query”", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(results, key = { it.fields.entries.joinToString("|") }) { PersonCard(it) }
                        if (results.isEmpty()) item { EmptyCard(query) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(dark: Boolean, onMenu: () -> Unit, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Menu") }
        Row(verticalAlignment = Alignment.CenterVertically) { Logo(Modifier.size(30.dp)); Spacer(Modifier.width(7.dp)); Text("V-Finder", fontWeight = FontWeight.Bold) }
        IconButton(onClick = onToggle) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme") }
    }
}

@Composable
private fun BrandHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Logo(Modifier.size(72.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("V-Finder", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("Search your people data instantly", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Logo(modifier: Modifier) {
    Icon(painterResource(com.vfinder.app.R.drawable.ic_vfinder_logo), "V-Finder", modifier)
}

@Composable
private fun SearchPanel(
    query: String,
    onQuery: (String) -> Unit,
    suggestions: List<String>,
    enabled: Boolean,
    onSuggestion: (String) -> Unit,
    onSearch: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Find a person", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Type a name to see matching suggestions", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Person name") },
                placeholder = { Text("e.g. Aman Kumar") },
                shape = RoundedCornerShape(18.dp)
            )
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        suggestions.forEach { name ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSuggestion(name) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Indigo, Cyan))), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(19.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSearch,
                enabled = enabled && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(10.dp))
                Text("SEARCH", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PersonCard(person: PersonRecord) {
    val name = person.name
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Indigo, Cyan))), contentAlignment = Alignment.Center) {
                    Text(initials(name), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("PERSON INFORMATION", color = Indigo, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE7F6ED)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("Exact match", color = Color(0xFF218A5A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            person.fields.filter { !isNameField(it.key) && it.value.isNotBlank() }.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Text(prettyKey(key), Modifier.width(125.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(value, Modifier.weight(1f), fontSize = 14.sp)
                }
            }
        }
    }
}

private fun initials(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

private fun prettyKey(key: String): String = key.replace('_', ' ').replace('-', ' ').trim().split(Regex("\\s+")).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

@Composable
private fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(18.dp)) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun EmptyCard(query: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text("No person found", fontWeight = FontWeight.Bold)
            Text("No Name field matched “$query”.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
