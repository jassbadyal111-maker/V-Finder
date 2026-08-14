package com.vfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileParserTest {
    @Test
    fun csvLoadsAllRecords() {
        val text = "name,phone,city\nJass,9999999999,Delhi\nAman,8888888888,Jaipur\n"
        val result = parseText(text, "people.csv")
        assertEquals(2, result.size)
        assertEquals("Jass", result[0].name)
    }

    @Test
    fun quotedCsvValuesKeepCommas() {
        val text = "name,address\nJass,\"Sector 1, Chandigarh\"\n"
        val result = parseText(text, "people.csv")
        assertEquals(1, result.size)
        assertEquals("Sector 1, Chandigarh", result.first().fields["address"])
    }

    @Test
    fun nameFieldDoesNotUseFatherName() {
        val text = "name,father_name,city\nAman Kumar,Rajesh Kumar,Rohtak\n"
        val result = parseText(text, "people.csv")
        assertEquals("Aman Kumar", result.first().name)
    }

    @Test
    fun jsonArrayLoadsAllRecords() {
        val text = "[{\"name\":\"Jass\",\"city\":\"Delhi\"},{\"name\":\"Aman\",\"city\":\"Jaipur\"}]"
        val result = parseText(text, "people.json")
        assertEquals(2, result.size)
        assertEquals("Aman", result[1].name)
    }

    @Test
    fun keyValueTextCanExposeName() {
        val text = "Name: Jass Badyal | Father Name: Rajesh | City: Delhi\n"
        val result = parseText(text, "people.txt")
        assertEquals("Jass Badyal", result.first().name)
        assertTrue(result.first().fields["Father Name"].orEmpty().contains("Rajesh"))
    }
}
