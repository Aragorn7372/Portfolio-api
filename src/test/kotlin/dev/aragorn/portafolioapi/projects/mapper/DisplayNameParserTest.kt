package dev.aragorn.portafolioapi.projects.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DisplayNameParserTest {

    private val repo = "pi-hole"

    @Test
    @DisplayName("nombre y descripción separados por |")
    fun nameAndDescription() {
        assertEquals(
            ParsedDescription("Pi-hole casero", "Bloqueador de anuncios para mi red"),
            DisplayNameParser.parse(repo, "Pi-hole casero | Bloqueador de anuncios para mi red"),
        )
    }

    @Test
    @DisplayName("solo cuenta la primera |, el resto se queda en la descripción")
    fun onlyFirstPipe() {
        assertEquals(
            ParsedDescription("Mi CV", "Web en Angular | con daisyUI"),
            DisplayNameParser.parse(repo, "Mi CV | Web en Angular | con daisyUI"),
        )
    }

    @Test
    @DisplayName("sin | usa el nombre del repo y deja la descripción tal cual")
    fun withoutPipe() {
        assertEquals(
            ParsedDescription(repo, "Una api para portfolios"),
            DisplayNameParser.parse(repo, "Una api para portfolios"),
        )
    }

    @Test
    @DisplayName("sin descripción o en blanco usa el nombre del repo")
    fun nullOrBlank() {
        assertEquals(ParsedDescription(repo, null), DisplayNameParser.parse(repo, null))
        assertEquals(ParsedDescription(repo, null), DisplayNameParser.parse(repo, "   "))
    }

    @Test
    @DisplayName("nombre vacío (| descripción) usa el nombre del repo")
    fun emptyName() {
        assertEquals(ParsedDescription(repo, "Solo descripción"), DisplayNameParser.parse(repo, "| Solo descripción"))
        assertEquals(ParsedDescription(repo, null), DisplayNameParser.parse(repo, " | "))
    }

    @Test
    @DisplayName("descripción vacía (Nombre |) deja la descripción a null")
    fun emptyDescription() {
        assertEquals(ParsedDescription("Nombre bonito", null), DisplayNameParser.parse(repo, "Nombre bonito |"))
    }

    @Test
    @DisplayName("un nombre demasiado largo no se trata como nombre")
    fun tooLongName() {
        val long = "a".repeat(DisplayNameParser.MAX_DISPLAY_NAME_LENGTH + 1)
        val text = "$long | resto"
        assertEquals(ParsedDescription(repo, text), DisplayNameParser.parse(repo, text))
    }

    @Test
    @DisplayName("quita espacios y admite saltos de línea en la descripción")
    fun whitespaceAndNewlines() {
        assertEquals(
            ParsedDescription("Nombre", "línea 1\nlínea 2"),
            DisplayNameParser.parse(repo, "  Nombre   |   línea 1\nlínea 2  "),
        )
    }
}
