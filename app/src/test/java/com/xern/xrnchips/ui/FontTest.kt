package com.xern.xrnchips.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * The fonts the app ships, read as TrueType files. MiSans Latin 4.007's static Bold and Heavy draw
 * "@" with the "&" outline, which put "&sam" on the table for a player named "@sam".
 */
class FontTest {
    private val fonts = File("src/main/res/font").listFiles { f -> f.extension == "ttf" }.orEmpty().sortedBy { it.name }

    /** One TrueType file: its tables, and a glyph's outline by character. */
    private class TrueType(file: File) {
        private val b = ByteBuffer.wrap(file.readBytes())
        private val tables: Map<String, Int> = (0 until (b.getShort(4).toInt() and 0xFFFF)).associate { i ->
            val at = 12 + 16 * i
            String(ByteArray(4) { b.get(at + it) }, Charsets.US_ASCII) to b.getInt(at + 8)
        }

        private fun u16(at: Int) = b.getShort(at).toInt() and 0xFFFF

        /** The glyph a character maps to, through the cmap's format 4 subtable. */
        fun glyph(char: Char): Int {
            val cmap = tables.getValue("cmap")
            for (i in 0 until u16(cmap + 2)) {
                val sub = cmap + b.getInt(cmap + 4 + 8 * i + 4)
                if (u16(sub) != 4) continue
                val segs = u16(sub + 6) / 2
                val ends = sub + 14
                val starts = ends + 2 * segs + 2
                val deltas = starts + 2 * segs
                val offsets = deltas + 2 * segs
                for (k in 0 until segs) {
                    if (char.code < u16(starts + 2 * k) || char.code > u16(ends + 2 * k)) continue
                    val delta = b.getShort(deltas + 2 * k).toInt()
                    val offset = u16(offsets + 2 * k)
                    if (offset == 0) return (char.code + delta) and 0xFFFF
                    val g = u16(offsets + 2 * k + offset + 2 * (char.code - u16(starts + 2 * k)))
                    return if (g == 0) 0 else (g + delta) and 0xFFFF
                }
            }
            return 0
        }

        /** A glyph's outline as stored in 'glyf'. */
        fun outline(glyph: Int): List<Byte> {
            val longOffsets = b.getShort(tables.getValue("head") + 50).toInt() == 1
            val loca = tables.getValue("loca")
            val (from, to) = if (longOffsets) {
                b.getInt(loca + 4 * glyph) to b.getInt(loca + 4 * glyph + 4)
            } else {
                2 * u16(loca + 2 * glyph) to 2 * u16(loca + 2 * glyph + 2)
            }
            val glyf = tables.getValue("glyf")
            return (glyf + from until glyf + to).map { b.get(it) }
        }
    }

    @Test
    fun everyShippedFontDrawsAtAsItsOwnGlyph() {
        assertTrue("no fonts found in src/main/res/font", fonts.isNotEmpty())
        for (file in fonts) {
            val font = TrueType(file)
            val at = font.glyph('@')
            val amp = font.glyph('&')
            assertNotEquals("${file.name} has no '@'", 0, at)
            assertFalse("${file.name} draws '@' as '&'", font.outline(at) == font.outline(amp))
        }
    }
}
