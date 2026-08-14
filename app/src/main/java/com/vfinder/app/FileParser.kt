package com.vfinder.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import java.io.BufferedReader
import java.nio.charset.StandardCharsets
import java.util.Locale

internal fun parseInputStream(input: InputStream, fileName: String, query: String): List<PersonRecord> {
    val text = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
    return parseText(text, fileName, query)
}

internal fun parseText(text: String, fileName: String, query: String): List<PersonRecord> {
    val normalized = query.trim().lowercase(Locale.getDefault())
    if (normalized.isBlank() || text.isBlank()) return emptyList()

    if (fileName.substringAfterLast('.', "").equals("json", true) || text.trimStart().startsWith("{") || text.trimStart().startsWith("[")) {
        return parseJson(text, normalized)
    }

    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    if (lines.isEmpty()) return emptyList()

    val delimiter = detectDelimiter(lines.first())
    if (delimiter != null && lines.size >= 2) {
        val headers = splitDelimitedLine(lines.first(), delimiter).mapIndexed { index, header ->
            header.trim().trim('"').ifBlank { "Column ${index + 1}" }
        }
        return lines.drop(1).mapNotNull { line ->
            val cells = splitDelimitedLine(line, delimiter)
            val fields = headers.mapIndexedNotNull { index, header ->
                cells.getOrNull(index)?.let { header to cleanCell(it) }
            }.toMap()
            if (fields.values.any { it.contains(normalized, ignoreCase = true) }) PersonRecord(fields) else null
        }
    }

    return lines.filter { it.contains(normalized, ignoreCase = true) }
        .map { PersonRecord(linkedMapOf("data" to it.trim())) }
}

private fun parseJson(text: String, normalized: String): List<PersonRecord> {
    val root = text.trimStart()
    return try {
        when {
            root.startsWith("[") -> {
                val array = JSONArray(text)
                (0 until array.length()).mapNotNull { index ->
                    jsonValueToRecord(array.get(index), normalized)
                }
            }
            root.startsWith("{") -> {
                val objectRoot = JSONObject(text)
                val data = objectRoot.opt("data")
                if (data is JSONArray) {
                    (0 until data.length()).mapNotNull { index -> jsonValueToRecord(data.get(index), normalized) }
                } else {
                    jsonValueToRecord(objectRoot, normalized)?.let(::listOf).orEmpty()
                }
            }
            else -> emptyList()
        }
    } catch (error: Exception) {
        throw IllegalArgumentException("Invalid JSON file: ${error.message ?: "malformed JSON"}", error)
    }
}

private fun jsonValueToRecord(value: Any, normalized: String): PersonRecord? {
    if (value !is JSONObject) {
        val text = value.toString()
        return if (text.contains(normalized, ignoreCase = true)) PersonRecord(mapOf("data" to text)) else null
    }

    val fields = linkedMapOf<String, String>()
    val keys = value.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val fieldValue = value.opt(key)
        if (fieldValue == null || fieldValue == JSONObject.NULL) continue
        fields[key] = when (fieldValue) {
            is JSONArray, is JSONObject -> fieldValue.toString()
            else -> fieldValue.toString()
        }
    }
    return if (fields.values.any { it.contains(normalized, ignoreCase = true) }) PersonRecord(fields) else null
}

private fun detectDelimiter(header: String): Char? {
    val candidates = listOf('\t', ',', ';')
    return candidates.maxByOrNull { delimiter -> splitDelimitedLine(header, delimiter).size }
        ?.takeIf { splitDelimitedLine(header, it).size > 1 }
}

private fun splitDelimitedLine(line: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                current.append('"')
                index++
            }
            char == '"' -> inQuotes = !inQuotes
            char == delimiter && !inQuotes -> {
                result += current.toString()
                current.clear()
            }
            else -> current.append(char)
        }
        index++
    }
    result += current.toString()
    return result
}

private fun cleanCell(value: String): String = value.trim().trim('"').replace(""""""", """)
