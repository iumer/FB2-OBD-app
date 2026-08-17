package com.fb2.obd

import com.fb2.obd.ui.ChromeCollapse
import org.junit.Assert.assertEquals
import org.junit.Test

class ChromeCollapseTest {

    @Test
    fun scrollUp_collapsesHeaderFirst() {
        val step = ChromeCollapse.onPreScroll(deltaY = -40f, collapsedPx = 0f, headerPx = 120f)
        assertEquals(40f, step.collapsedPx, 0.01f)
        assertEquals(-40f, step.consumedY, 0.01f)
    }

    @Test
    fun scrollUp_stopsAtFullCollapse() {
        val step = ChromeCollapse.onPreScroll(deltaY = -80f, collapsedPx = 100f, headerPx = 120f)
        assertEquals(120f, step.collapsedPx, 0.01f)
        assertEquals(-20f, step.consumedY, 0.01f)
    }

    @Test
    fun scrollDown_doesNotExpandOnPreScroll() {
        val step = ChromeCollapse.onPreScroll(deltaY = 30f, collapsedPx = 80f, headerPx = 120f)
        assertEquals(80f, step.collapsedPx, 0.01f)
        assertEquals(0f, step.consumedY, 0.01f)
    }

    @Test
    fun leftoverScrollDown_expandsHeader() {
        val step = ChromeCollapse.onPostScroll(availableY = 50f, collapsedPx = 80f, headerPx = 120f)
        assertEquals(30f, step.collapsedPx, 0.01f)
        assertEquals(50f, step.consumedY, 0.01f)
    }

    @Test
    fun leftoverScrollDown_stopsAtFullyShown() {
        val step = ChromeCollapse.onPostScroll(availableY = 90f, collapsedPx = 40f, headerPx = 120f)
        assertEquals(0f, step.collapsedPx, 0.01f)
        assertEquals(40f, step.consumedY, 0.01f)
    }
}
