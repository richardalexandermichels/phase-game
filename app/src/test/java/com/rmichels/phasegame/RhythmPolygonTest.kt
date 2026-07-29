package com.rmichels.phasegame

import org.junit.Assert.assertEquals
import org.junit.Test

class RhythmPolygonTest {
    @Test
    fun regularPolygonVertices_createsOneVertexPerSide() {
        val vertices = regularPolygonVertices(
            sideCount = 4,
            rotationRadians = 0f
        )

        assertEquals(4, vertices.size)
        assertEquals(1f, vertices[0].x, 0.0001f)
        assertEquals(0f, vertices[0].y, 0.0001f)
        assertEquals(0f, vertices[1].x, 0.0001f)
        assertEquals(1f, vertices[1].y, 0.0001f)
    }
}