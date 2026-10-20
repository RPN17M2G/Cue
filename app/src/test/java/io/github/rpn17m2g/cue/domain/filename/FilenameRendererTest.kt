package io.github.rpn17m2g.cue.domain.filename

import io.github.rpn17m2g.cue.domain.model.TagDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameRendererTest {

    private val draft = TagDraft(
        title = "Get Lucky",
        artist = "Daft Punk",
        album = "Random Access Memories",
        year = "2013",
        trackNumber = 8,
    )

    @Test
    fun `default template renders artist and title`() {
        assertEquals("Daft Punk - Get Lucky", FilenameRenderer.render(draft))
    }

    @Test
    fun `all known tokens are rendered`() {
        val template = "{track} {artist} - {title} ({album}, {year})"

        assertEquals(
            "8 Daft Punk - Get Lucky (Random Access Memories, 2013)",
            FilenameRenderer.render(draft, template),
        )
    }

    @Test
    fun `unknown tokens render nothing`() {
        assertEquals(
            "Daft Punk Get Lucky",
            FilenameRenderer.render(draft, "{artist} {mood} {title}"),
        )
    }

    @Test
    fun `blank values collapse duplicate separators`() {
        assertEquals(
            "Daft Punk - Get Lucky",
            FilenameRenderer.render(draft.copy(album = ""), "{artist} - {album} - {title}"),
        )
    }

    @Test
    fun `blank artist leaves a clean title`() {
        assertEquals("Get Lucky", FilenameRenderer.render(draft.copy(artist = "")))
    }

    @Test
    fun `empty result falls back to unknown track`() {
        assertEquals("Unknown track", FilenameRenderer.render(TagDraft(), "{artist} - {title}"))
    }

    @Test
    fun `sanitize replaces illegal and control characters`() {
        assertEquals("Bad Name", FilenameRenderer.sanitize("  Bad<>:\"/\\|?*\u0001Name.  "))
    }

    @Test
    fun `sanitize trims leading and trailing dots and spaces`() {
        assertEquals("Song", FilenameRenderer.sanitize(" ..Song.. "))
    }

    @Test
    fun `sanitize caps the name at 120 characters`() {
        assertEquals(120, FilenameRenderer.sanitize("a".repeat(200)).length)
    }

    @Test
    fun `sanitize truncates by code point without splitting an emoji`() {
        val sanitized = FilenameRenderer.sanitize("😀".repeat(130))

        assertEquals(120, sanitized.codePointCount(0, sanitized.length))
        assertEquals("😀".repeat(120), sanitized)
    }

    @Test
    fun `sanitize never returns an empty name`() {
        assertEquals("Unknown track", FilenameRenderer.sanitize("<>:\"/\\|?*"))
        assertEquals("Unknown track", FilenameRenderer.sanitize("   "))
    }

    @Test
    fun `sanitize keeps Hebrew characters`() {
        assertEquals("שלום עולם", FilenameRenderer.sanitize("שלום עולם"))
    }

    @Test
    fun `render does not append an extension`() {
        assertEquals("Daft Punk - Get Lucky", FilenameRenderer.render(draft))
    }
}
