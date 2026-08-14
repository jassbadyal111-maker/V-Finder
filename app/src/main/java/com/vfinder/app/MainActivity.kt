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

data class PersonRecord(val fields: Map<String, String>)

private val NAME_KEYS = setOf("name", "full name", "person name", "customer name", "member name")
private val Indigo = Color(0xFF5846F2)
private val Cyan = Color(0xFF19CFF2)
private val LightBg = Color(0xFFF5F7FC)
private val DarkBg = Color(0xFF090D18)

private fun normalizedKey(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace('_', ' ')
    .replace('-', ' ')
    .trim()
    .replace(Regex("\\s+"), " ")

private fun normalize(value: String): String = value.trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), " ")

private fun personName(record: PersonRecord): String = record.fields.entries
    .firstOrNull { entry -> entry.value.isNotBlank() && normalizedKey(entry.key) in NAME_KEYS }
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var darkMode by rememberSaveable { mutableStateOf(isSystemInDarkTheme()) }
    var query by rememberSaveable { mutableStateOf("") }
    var fileName by rememberSaveable { mutableStateOf("No file selected") }
    var records by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var results by remember { mutableStateOf(emptyList<PersonRecord>()) }
    var searched by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        fileName = displayName(context, uri)
        val selectedName = fileName
        loading = true
        records = emptyList()
        results = emptyList()
        query = ""
        searched = false
        errorMessage = null

        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.use { input ->
                        parseInputStream(input, selectedName)
                    } ?: throw IllegalStateException("Unable to open the selected file.")
                }
            }

            parsed.onSuccess { loaded ->
                records = loaded
                loading = false
            }.onFailure { throwable ->
                records = emptyList()
                loading = false
                errorMessage = throwable.message ?: "Unable to read the selected file."
            }
        }
    }

    val suggestions = remember(query, records) {
        val q = normalize(query)
        if (q.isBlank()) {
            emptyList()
        } else {
            records.asSequence()
                .map(::personName)
                .filter { it.isNotBlank() }
                .distinctBy(::normalize)
                .filter { normalize(it).contains(q) }
                .sortedWith(
                    compareBy<String>(
                        { !normalize(it).startsWith(q) },
                        { it.length },
                        { normalize(it) }
                    )
                )
                .take(6)
                .toList()
        }
    }

    fun performSearch() {
        keyboard?.hide()
        val q = normalize(query)
        when {
            q.isBlank() -> errorMessage = "Enter a person name."
            records.isEmpty() -> errorMessage = "Add a data file from the side menu first."
            else -> {
                errorMessage = null
                searched = true
                results = records.filter { normalize(personName(it)).contains(q) }
            }
        }
    }

    val lightColors = lightColorScheme(
        primary = Indigo,
        background = LightBg,
        surface = Color.White,
        surfaceVariant = Color(0xFFE9ECF5),
        onBackground = Color(0xFF111522),
        onSurface = Color(0xFF111522),
        onSurfaceVariant = Color(0xFF626A7A)
    )

    val darkColors = darkColorScheme(
        primary = Color(0xFFA49CFF),
        background = DarkBg,
        surface = Color(0xFF111827),
        surfaceVariant = Color(0xFF1B2435),
        onBackground = Color(0xFFF3F5FA),
        onSurface = Color(0xFFF3F5FA),
        onSurfaceVariant = Color(0xFFAEB7C8)
    )

    MaterialTheme(colorScheme = if (darkMode) darkColors else lightColors) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Logo(Modifier.size(48.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("V-Finder", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "Data source",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { scope.launch { drawerState.close() } }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        NavigationDrawerItem(
                            label = { Text("Add data file", fontWeight = FontWeight.Bold) },
                            selected = false,
                            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            onClick = {
                                picker.launch(arrayOf("text/*", "application/json", "text/csv"))
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "CURRENT FILE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        fileName,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (loading) {
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                if (records.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "${records.size} records loaded",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = {
                                            records = emptyList()
                                            results = emptyList()
                                            fileName = "No file selected"
                                            query = ""
                                            searched = false
                                            errorMessage = null
                                        }
                                    ) {
                                        Text("Remove file")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        TopBar(
                            darkMode = darkMode,
                            onMenu = { scope.launch { drawerState.open() } },
                            onTheme = { darkMode = !darkMode }
                        )
                    }
                    item { BrandHeader() }
                    item {
                        SearchPanel(
                            query = query,
                            suggestions = suggestions,
                            enabled = records.isNotEmpty() && !loading,
                            onQueryChange = {
                                query = it
                                searched = false
                                errorMessage = null
                            },
                            onSuggestionClick = {
                                query = it
                                searched = false
                                errorMessage = null
                            },
                            onSearch = ::performSearch
                        )
                    }
                    errorMessage?.let { message ->
                        item { MessageCard(message) }
                    }
                    if (searched && errorMessage == null) {
                        item {
                            Text(
                                text = "${results.size} result${if (results.size == 1) "" else "s"}",
                                color = Color(0xFF218A5A),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(
                            items = results,
                            key = { record -> record.fields.entries.joinToString("\u0001") }
                        ) { record ->
                            PersonCard(record)
                        }
                        if (results.isEmpty()) {
                            item { EmptyCard(query) }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    darkMode: Boolean,
    onMenu: () -> Unit,
    onTheme: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Default.Menu, contentDescription = "Open menu")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(Modifier.size(30.dp))
            Spacer(Modifier.width(7.dp))
            Text("V-Finder", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        IconButton(onClick = onTheme) {
            Icon(
                imageVector = if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Theme"
            )
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo(Modifier.size(74.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("V-Finder", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Search your people data instantly",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Logo(modifier: Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_vfinder_logo),
        contentDescription = "V-Finder logo",
        modifier = modifier
    )
}

@Composable
private fun SearchPanel(
    query: String,
    suggestions: List<String>,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onSearch: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Find a person", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Type a name to see matching suggestions",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Person name") },
                placeholder = { Text("e.g. Aman Kumar") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp)
            )

            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        suggestions.forEach { name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSuggestionClick(name) }
                                    .padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(Indigo, Cyan))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        initials(name),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    name,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSearch,
                enabled = enabled && query.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text("SEARCH", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun PersonCard(record: PersonRecord) {
    val name = personName(record).ifBlank { "Unnamed person" }

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Indigo, Cyan))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initials(name),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "PERSON INFORMATION",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()

            record.fields.entries
                .filter { entry ->
                    entry.value.isNotBlank() && normalizedKey(entry.key) !in NAME_KEYS
                }
                .take(8)
                .forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            pretty(entry.key),
                            modifier = Modifier.width(120.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            entry.value,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp
                        )
                    }
                }
        }
    }
}

@Composable
private fun MessageCard(message: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun EmptyCard(query: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("No person found", fontWeight = FontWeight.Bold)
            Text(
                "No Name field matched \"$query\".",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun initials(name: String): String = name
    .trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { token -> token.first().uppercase() }

private fun pretty(key: String): String = key
    .replace('_', ' ')
    .replace('-', ' ')
    .trim()
    .split(Regex("\\s+"))
    .joinToString(" ") { word ->
        word.replaceFirstChar { character -> character.uppercase() }
    }

private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
    ?: uri.lastPathSegment?.substringAfterLast('/')
    ?: "Selected file"
