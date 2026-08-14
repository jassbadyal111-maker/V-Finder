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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
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

private val FinderIndigo = Color(0xFF5846F2)
private val FinderCyan = Color(0xFF19CFF2)
private val FinderLightBg = Color(0xFFF5F7FC)
private val FinderDarkBg = Color(0xFF090D18)

private val FinderLight = lightColorScheme(
    primary = FinderIndigo,
    onPrimary = Color.White,
    background = FinderLightBg,
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECF5),
    onBackground = Color(0xFF111522),
    onSurface = Color(0xFF111522),
    onSurfaceVariant = Color(0xFF626A7A)
)

private val FinderDark = darkColorScheme(
    primary = Color(0xFFA49CFF),
    onPrimary = Color(0xFF1B1745),
    background = FinderDarkBg,
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1B2435),
    onBackground = Color(0xFFF3F5FA),
    onSurface = Color(0xFFF3F5FA),
    onSurfaceVariant = Color(0xFFAEB7C8)
)

class FinderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FinderApp() }
    }
}

@Composable
private fun FinderApp() {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val systemDark = isSystemInDarkTheme()
    var dark by rememberSaveable { mutableStateOf(systemDark) }
    var fileName by rememberSaveable { mutableStateOf("No file selected") }
    var query by rememberSaveable { mutableStateOf("") }
    var allRows by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var results by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var searched by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        fileName = displayName(resolver, uri)
        loading = true
        searching = false
        searched = false
        results = emptyList()
        allRows = emptyList()
        query = ""
        error = null
        runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.use { parseInputStream(it, fileName, "") }
                        ?: error("Unable to open the selected file.")
                }
            }
            parsed.onSuccess {
                allRows = it
                loading = false
                drawerState.close()
            }.onFailure {
                loading = false
                error = it.message ?: "Could not read the selected file."
            }
        }
    }

    val suggestions = remember(query, allRows) {
        val q = query.trim()
        if (q.isBlank()) emptyList() else allRows.asSequence()
            .mapNotNull { exactPersonName(it).takeIf(String::isNotBlank) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .filter { it.contains(q, ignoreCase = true) }
            .sortedWith(compareBy({ !it.startsWith(q, ignoreCase = true) }, { it.length }, { it.lowercase(Locale.ROOT) }))
            .take(6)
            .toList()
    }

    fun search() {
        keyboard?.hide()
        val q = query.trim()
        if (q.isBlank()) {
            error = "Enter a person name before searching."
            return
        }
        if (allRows.isEmpty()) {
            error = "Add a data file from the side menu first."
            return
        }
        searching = true
        searched = true
        error = null
        scope.launch {
            results = withContext(Dispatchers.Default) {
                allRows.filter { exactPersonName(it).contains(q, ignoreCase = true) }
            }
            searching = false
        }
    }

    MaterialTheme(colorScheme = if (dark) FinderDark else FinderLight) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Drawer(
                        fileName = fileName,
                        loading = loading,
                        onPick = { picker.launch(arrayOf("text/*", "text/csv", "application/json")) },
                        onClose = { scope.launch { drawerState.close() } },
                        onClear = {
                            fileName = "No file selected"
                            allRows = emptyList()
                            results = emptyList()
                            query = ""
                            searched = false
                            error = null
                        }
                    )
                }
            }
        ) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { inset ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(inset).padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { TopBar(dark, { scope.launch { drawerState.open() } }, { dark = !dark }) }
                    item { BrandHeader() }
                    item {
                        SearchPanel(
                            query = query,
                            suggestions = suggestions,
                            enabled = allRows.isNotEmpty() && !loading,
                            searching = searching,
                            onQuery = { query = it; searched = false; error = null },
                            onSuggestion = { query = it; searched = false; error = null },
                            onSearch = ::search
                        )
                    }
                    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape)) }
                    if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape)) }
                    error?.let { message -> item { MessageCard(message) } }
                    if (searched && error == null) {
                        item { ResultSummary(results.size, query) }
                        items(results, key = { it.fields.entries.joinToString("\u0001") }) { PersonInformationCard(it) }
                        if (results.isEmpty()) item { EmptyResult(query) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun exactPersonName(record: PersonRecord): String {
    val preferred = listOf("name", "full name", "person name", "customer name", "member name")
    return record.fields.entries.firstOrNull { (key, value) ->
        val normalized = key.lowercase(Locale.ROOT).replace("_", " ").replace("-", " ").trim()
        value.isNotBlank() && normalized in preferred
    }?.value?.trim().orEmpty()
}

@Composable
private fun TopBar(dark: Boolean, onMenu: () -> Unit, onTheme: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Open menu") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(Modifier.size(30.dp))
            Spacer(Modifier.width(7.dp))
            Text("V-Finder", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        IconButton(onClick = onTheme) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme") }
    }
}

@Composable
private fun BrandHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoMark(Modifier.size(76.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("V-Finder", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
            Text("Search your people data instantly", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LogoMark(modifier: Modifier) {
    Icon(painterResource(R.drawable.ic_vfinder_logo), "V-Finder logo", modifier = modifier)
}

@Composable
private fun SearchPanel(query: String, suggestions: List<String>, enabled: Boolean, searching: Boolean, onQuery: (String) -> Unit, onSuggestion: (String) -> Unit, onSearch: () -> Unit) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Find a person", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("Type a name to see matching suggestions", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                enabled = enabled && !searching,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear") } },
                placeholder = { Text("Aman Kumar") },
                label = { Text("Person name") },
                shape = RoundedCornerShape(18.dp)
            )
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        suggestions.forEach { name ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSuggestion(name) }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(FinderIndigo, FinderCyan))), contentAlignment = Alignment.Center) {
                                    Text(initials(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(11.dp))
                                Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSearch,
                enabled = enabled && query.isNotBlank() && !searching,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinderIndigo)
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(10.dp))
                Text(if (searching) "SEARCHING" else "SEARCH", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun PersonInformationCard(record: PersonRecord) {
    val name = exactPersonName(record).ifBlank { "Unnamed person" }
    val displayFields = record.fields.entries.filter { (key, _) ->
        val normalized = key.lowercase(Locale.ROOT).replace("_", " ").replace("-", " ").trim()
        normalized !in setOf("name", "full name", "person name", "customer name", "member name")
    }
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(Brush.linearGradient(listOf(FinderIndigo, FinderCyan))), contentAlignment = Alignment.Center) {
                    Text(initials(name), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("PERSON INFORMATION", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                }
                Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE6F6EC)).padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, null, tint = Color(0xFF168A48), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Match", color = Color(0xFF168A48), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            displayFields.take(8).forEach { (key, value) ->
                InformationRow(key, value)
            }
        }
    }
}

@Composable
private fun InformationRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.Top) {
        Icon(fieldIcon(key), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(prettyLabel(key), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.width(112.dp))
        Text(value.ifBlank { "—" }, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
}

private fun fieldIcon(key: String) = when {
    key.contains("phone", true) || key.contains("mobile", true) -> Icons.Default.Phone
    key.contains("mail", true) -> Icons.Default.Email
    key.contains("address", true) || key.contains("city", true) || key.contains("location", true) -> Icons.Default.LocationOn
    key.contains("birth", true) || key.contains("date", true) -> Icons.Default.CalendarToday
    else -> Icons.Default.Person
}

private fun prettyLabel(value: String): String = value.replace("_", " ").replace("-", " ").trim().split(" ").filter(String::isNotBlank).joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }

private fun initials(name: String): String = name.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "VF" }

@Composable
private fun ResultSummary(count: Int, query: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text("$count ${if (count == 1) "result" else "results"}", color = Color(0xFF258A55), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text("for “$query”", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Drawer(fileName: String, loading: Boolean, onPick: () -> Unit, onClose: () -> Unit, onClear: () -> Unit) {
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
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        NavigationDrawerItem(label = { Text("Add data file", fontWeight = FontWeight.SemiBold) }, selected = false, icon = { Icon(Icons.Default.FolderOpen, null) }, onClick = onPick)
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("CURRENT DATA SOURCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(fileName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (loading) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (fileName != "No file selected") { Spacer(Modifier.height(6.dp)); TextButton(onClick = onClear) { Text("Remove file") } }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("CSV • TSV • TXT • JSON", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageCard(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyResult(query: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text("No exact name match", fontWeight = FontWeight.Bold)
            Text("No person named “$query” was found in the selected data file.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

private fun displayName(resolver: ContentResolver, uri: Uri): String {
    var cursor: Cursor? = null
    return try {
        cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor?.moveToFirst() == true) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
    } catch (_: Exception) {
        uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
    } finally { cursor?.close() }
}
