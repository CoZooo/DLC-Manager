package com.cozooo.dclanguage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DcKotlinInteropTest {
    @Test
    fun `Java API supports typed Kotlin reads writes and validation`() {
        val language = DcLanguages.create()
        val basic = language.registerMainCategory("basic", true)
        val identifier = language.addField(basic, DcField.string("identifier"), true)
        val enabled = language.addField(basic, DcField.bool("enabled").defaultValue(true), false)
        val tags = language.addField(basic, DcField.list("tags", DcTypes.STRING), false)
        val document = DcDocuments.readText("[basic]\nidentifier = \"main\"\n")

        assertEquals("main", document.get(basic, identifier))
        assertTrue(document.get(basic, enabled))
        document.set(basic, tags, listOf("one", "two"))
        document.set(basic, enabled, false)

        assertTrue(language.validate(document).valid())
        val saved = DcDocuments.readText(document.toText())
        assertEquals(listOf("one", "two"), saved.get(basic, tags))
        assertEquals(false, saved.get(basic, enabled))
    }
}
