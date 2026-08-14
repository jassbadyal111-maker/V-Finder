package com.vfinder.app

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

internal fun parseInputStream(input: InputStream, fileName: String, query: String): List<PersonRecord> {
    val text = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
    return parseText(text, fileName, query)
}

internal fun parseText(text: String, fileName: String, query: String): List<PersonRecord> {
    val normalized = query.trim().lowercase(Locale.ROOT)
    if (text.isBlank()) return emptyList()

    val trimmed = text.trimStart()
    if (fileName.substringAfterLast('.', "").equals("json", true) || trimmed.startsWith("{") || trimmed.startsWith("[")) {
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
            if (normalized.isBlank() || fields.values.any { it.contains(normalized, ignoreCase = true) }) PersonRecord(fields) else null
        }
    }

    return lines.filter { normalized.isBlank() || it.contains(normalized, ignoreCase = true) }
        .map { PersonRecord(linkedMapOf("data" to it.trim())) }
}

private fun parseJson(text: String, normalized: String): List<PersonRecord> {
    return try {
        val root = JsonParser.parseString(text)
        when {
            root.isJsonArray -> parseJsonArray(root.asJsonArray, normalized)
            root.isJsonObject -> {
                val rootObject = root.asJsonObject
                val data = rootObject.get("data")
                if (data != null && data.isJsonArray) {
                    parseJsonArray(data.asJsonArray, normalized)
                } else {
                    jsonObjectToRecord(rootObject, normalized)?.let(::listOf).orEmpty()
                }
            }
            root.isJsonPrimitive -> jsonPrimitiveToRecord(root, normalized)?.let(::listOf).orEmpty()
            else -> emptyList()
        }
    } catch (error: Exception) {
        throw IllegalArgumentException("Invalid JSON file: ${error.message ?: "malformed JSON"}", error)
    }
}

private fun parseJsonArray(array: JsonArray, normalized: String): List<PersonRecord> =
    array.mapNotNull { value -> jsonElementToRecord(value, normalized) }

private fun jsonElementToRecord(value: JsonElement, normalized: String): PersonRecord? = when {
    value.isJsonObject -> jsonObjectToRecord(value.asJsonObject, normalized)
    value.isJsonArray -> {
        val raw = value.toString()
        if (normalized.isBlank() || raw.contains(normalized, ignoreCase = true)) PersonRecord(mapOf("data" to raw)) else null
    }
    value.isJsonPrimitive -> jsonPrimitiveToRecord(value, normalized)
    else -> null
}

private fun jsonObjectToRecord(value: JsonObject, normalized: String): PersonRecord? {
    val fields = linkedMapOf<String, String>()
    for ((key, fieldValue) in value.entrySet()) {
        if (fieldValue.isJsonNull) continue
        fields[key] = when {
            fieldValue.isJsonObject || fieldValue.isJsonArray -> fieldValue.toString()
            fieldValue.isJsonPrimitive -> fieldValue.asJsonPrimitive.asString
            else -> fieldValue.toString()
        }
    }
    return if (normalized.isBlank() || fields.values.any { it.contains(normalized, ignoreCase = true) }) PersonRecord(fields) else null
}

private fun jsonPrimitiveToRecord(value: JsonElement, normalized: String): PersonRecord? {
    val raw = value.asString
    return if (normalized.isBlank() || raw.contains(normalized, ignoreCase = true)) PersonRecord(mapOf("data" to raw)) else null
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

private fun cleanCell(value: String): String = value.trim().trim('"')
