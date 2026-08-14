package com.vfinder.app

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

internal fun parseInputStream(input: InputStream, fileName: String): List<PersonRecord> {
    val text = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
    return parseText(text, fileName)
}

internal fun parseText(text: String, fileName: String): List<PersonRecord> {
    if (text.isBlank()) return emptyList()
    val trimmed = text.trimStart()
    return if (fileName.substringAfterLast('.', "").equals("json", true) || trimmed.startsWith("{") || trimmed.startsWith("[")) parseJson(text) else parseDelimitedOrText(text)
}

private fun parseDelimitedOrText(text: String): List<PersonRecord> {
    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    if (lines.isEmpty()) return emptyList()
    val delimiter = detectDelimiter(lines.first())
    if (delimiter != null && lines.size >= 2) {
        val headers = splitDelimitedLine(lines.first(), delimiter).mapIndexed { index, header -> cleanCell(header).ifBlank { "Column ${index + 1}" } }
        return lines.drop(1).map { line ->
            val cells = splitDelimitedLine(line, delimiter)
            val fields = LinkedHashMap<String, String>()
            headers.forEachIndexed { index, header -> fields[header] = cells.getOrNull(index)?.let(::cleanCell).orEmpty() }
            PersonRecord(fields)
        }
    }
    return lines.map { line ->
        val fields = parseKeyValueLine(line)
        if (fields.isNotEmpty()) PersonRecord(fields) else PersonRecord(linkedMapOf("Data" to line.trim()))
    }
}

private fun parseKeyValueLine(line: String): LinkedHashMap<String, String> {
    val fields = LinkedHashMap<String, String>()
    for (part in line.split(Regex("\\s*[|;]\\s*"))) {
        val separator = part.indexOf(':').takeIf { it > 0 } ?: part.indexOf('=').takeIf { it > 0 } ?: continue
        val key = part.substring(0, separator).trim()
        val value = part.substring(separator + 1).trim()
        if (key.isNotBlank() && value.isNotBlank()) fields[key] = value
    }
    return fields
}

private fun parseJson(text: String): List<PersonRecord> {
    return try {
        val root = JsonParser.parseString(text)
        when {
            root.isJsonArray -> parseJsonArray(root.asJsonArray)
            root.isJsonObject -> {
                val jsonObject = root.asJsonObject
                val data = jsonObject.entrySet().firstOrNull { it.key.equals("data", true) }?.value
                if (data?.isJsonArray == true) parseJsonArray(data.asJsonArray) else listOf(jsonObjectToRecord(jsonObject))
            }
            root.isJsonPrimitive -> listOf(PersonRecord(linkedMapOf("Data" to root.asString)))
            else -> emptyList()
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid JSON file: ${e.message ?: "malformed JSON"}", e)
    }
}

private fun parseJsonArray(array: JsonArray): List<PersonRecord> = array.mapNotNull { element ->
    when {
        element.isJsonObject -> jsonObjectToRecord(element.asJsonObject)
        element.isJsonPrimitive -> PersonRecord(linkedMapOf("Data" to element.asString))
        else -> null
    }
}

private fun jsonObjectToRecord(jsonObject: JsonObject): PersonRecord {
    val fields = LinkedHashMap<String, String>()
    jsonObject.entrySet().forEach { (key, value) ->
        if (!value.isJsonNull) {
            fields[key] = when {
                value.isJsonObject || value.isJsonArray -> value.toString()
                value.isJsonPrimitive -> value.asJsonPrimitive.asString
                else -> value.toString()
            }
        }
    }
    return PersonRecord(fields)
}

private fun detectDelimiter(header: String): Char? {
    val candidates = listOf(',', '\t', ';')
    return candidates.maxByOrNull { splitDelimitedLine(header, it).size }?.takeIf { splitDelimitedLine(header, it).size > 1 }
}

private fun splitDelimitedLine(line: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
            c == '"' -> quoted = !quoted
            c == delimiter && !quoted -> { result += current.toString(); current.clear() }
            else -> current.append(c)
        }
        i++
    }
    result += current.toString()
    return result
}

private fun cleanCell(value: String): String = value.trim().trim('"')
