package com.itermeum.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteFormatTest {
    private fun enter(old: String, at: Int = old.length): NoteFormat.Edit? {
        val new = old.substring(0, at) + "\n" + old.substring(at)
        return NoteFormat.afterEnter(old, new, at + 1)
    }

    @Test fun `enter on a bullet starts the next one`() {
        assertEquals(NoteFormat.Edit("• хлеб\n• ", 9), enter("• хлеб"))
    }

    @Test fun `enter on a number counts on`() {
        assertEquals(NoteFormat.Edit("Темы:\n1. Труд\n2. ", 17), enter("Темы:\n1. Труд"))
    }

    @Test fun `enter on an empty item ends the list`() {
        assertEquals(NoteFormat.Edit("• хлеб\n", 7), enter("• хлеб\n• "))
    }

    @Test fun `plain lines and other edits are left alone`() {
        assertNull(enter("Просто текст"))
        assertNull(NoteFormat.afterEnter("• а", "• аб", 4))
    }

    @Test fun `enter in the middle of an item splits it`() {
        assertEquals(NoteFormat.Edit("• хле\n• б", 8), enter("• хлеб", 5))
    }

    @Test fun `toggles add, switch and remove the prefix of the current line`() {
        assertEquals(NoteFormat.Edit("• молитва", 9), NoteFormat.toggleBullet("молитва", 7))
        assertEquals(NoteFormat.Edit("молитва", 7), NoteFormat.toggleBullet("• молитва", 9))
        assertEquals(NoteFormat.Edit("1. молитва", 10), NoteFormat.toggleNumber("• молитва", 9))
        assertEquals(NoteFormat.Edit("1. а\n2. б", 9), NoteFormat.toggleNumber("1. а\nб", 6))
    }
}
