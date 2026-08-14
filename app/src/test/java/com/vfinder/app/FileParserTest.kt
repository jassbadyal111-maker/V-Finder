package com.vfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileParserTest {
    @Test
    fun csvSearchMatchesAcrossFields() {
        val text = "name,phone,city\nJass,9999999999,Delhi\nAman,8888888888,Jaipur\n"
        val result = parseText(text, "people.csv", "jass")

        assertEquals(1, result.size)
        assertEquals("Jass", result.first().fields["name"])
        assertEquals("Delhi", result.first().fields["city"])
    }

    @Test
    fun quotedCsvValuesKeepCommas() {
        val text = "name,address\nJass,\"Sector 1, Chandigarh\"\n"
        val result = parseText(text, "people.csv", "chandigarh")

        assertEquals(1, result.size)
        assertEquals("Sector 1, Chandigarh", result.first().fields["address"])
    }

    @Test
    fun jsonArraySearchWorks() {
        val text = "[{\"name\":\"Jass\",\"city\":\"Delhi\"},{\"name\":\"Aman\",\"city\":\"Jaipur\"}]"
        val result = parseText(text, "people.json", "jass")

        assertEquals(1, result.size)
        assertEquals("Jass", result.first().fields["name"])
    }

    @Test
    fun plainTextSearchWorks() {
        val text = "Jass - Delhi\nAman - Jaipur\n"
        val result = parseText(text, "people.txt", "jass")

        assertEquals(1, result.size)
        assertTrue(result.first().fields["data"].orEmpty().contains("Jass"))
    }
}
