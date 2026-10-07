package com.updownupdown.cards.watch.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every language has every string the English has, with the same arguments,
 * the same number of rules lines, and the same markdown on each.
 */
class TranslationsTest {
    private val res = File("src/main/res")
    private val placeholder = Regex("""%(\d+\$)?[sd%]""")

    /** Each translatable string or array, by name, from [folder]'s XML files. */
    private fun strings(folder: String): Map<String, List<String>> {
        val files = File(res, folder).listFiles { f -> f.extension == "xml" } ?: return emptyMap()
        return files.flatMap { file ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val nodes = root.childNodes
            (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
                .filter { (it.tagName == "string" || it.tagName == "string-array") && it.getAttribute("translatable") != "false" }
                .map { element ->
                    val items = element.getElementsByTagName("item")
                    element.getAttribute("name") to
                        if (element.tagName == "string") listOf(element.textContent)
                        else (0 until items.length).map { items.item(it).textContent }
                }
        }.toMap()
    }

    @Test
    fun everyLanguageHasEveryString() {
        val english = strings("values")
        val languages = AppLanguage.entries.filter { it != AppLanguage.English }
        for (language in languages) {
            // Android still calls Indonesian "in".
            val folder = "values-" + if (language.tag == "id") "in" else language.tag
            val translated = strings(folder)
            for ((name, lines) in english) {
                val theirs = translated[name]
                assertTrue("$folder lacks $name", theirs != null)
                assertEquals("$folder/$name: lines", lines.size, theirs!!.size)
                lines.zip(theirs).forEachIndexed { i, (en, tr) ->
                    val where = "$folder/$name[$i]: $tr"
                    assertEquals(where, placeholder.findAll(en).map { it.value }.sorted().toList(), placeholder.findAll(tr).map { it.value }.sorted().toList())
                    assertEquals(where, en.startsWith("## "), tr.startsWith("## "))
                    assertEquals(where, en.startsWith("- "), tr.startsWith("- "))
                    assertEquals(where, 0, tr.split("**").size.minus(1) % 2)
                    assertTrue(where, tr.isNotBlank())
                }
            }
            assertEquals("$folder has strings English hasn't", emptySet<String>(), translated.keys - english.keys)
        }
    }
}
