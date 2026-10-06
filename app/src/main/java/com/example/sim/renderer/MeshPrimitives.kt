package com.example.sim.renderer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

object MeshPrimitives {

    fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    // Standard Unit Cube: Position (3), Normal (3)
    // 36 vertices (6 faces * 2 triangles * 3 vertices)
    fun createCubeBuffer(): Pair<FloatBuffer, Int> {
        val vertices = floatArrayOf(
            // Front face (Z = 0.5)
            -0.5f, -0.5f,  0.5f,   0f,  0f,  1f,
             0.5f, -0.5f,  0.5f,   0f,  0f,  1f,
             0.5f,  0.5f,  0.5f,   0f,  0f,  1f,
            -0.5f, -0.5f,  0.5f,   0f,  0f,  1f,
             0.5f,  0.5f,  0.5f,   0f,  0f,  1f,
            -0.5f,  0.5f,  0.5f,   0f,  0f,  1f,

            // Back face (Z = -0.5)
            -0.5f, -0.5f, -0.5f,   0f,  0f, -1f,
             0.5f,  0.5f, -0.5f,   0f,  0f, -1f,
             0.5f, -0.5f, -0.5f,   0f,  0f, -1f,
            -0.5f, -0.5f, -0.5f,   0f,  0f, -1f,
            -0.5f,  0.5f, -0.5f,   0f,  0f, -1f,
             0.5f,  0.5f, -0.5f,   0f,  0f, -1f,

            // Top face (Y = 0.5)
            -0.5f,  0.5f, -0.5f,   0f,  1f,  0f,
            -0.5f,  0.5f,  0.5f,   0f,  1f,  0f,
             0.5f,  0.5f,  0.5f,   0f,  1f,  0f,
            -0.5f,  0.5f, -0.5f,   0f,  1f,  0f,
             0.5f,  0.5f,  0.5f,   0f,  1f,  0f,
             0.5f,  0.5f, -0.5f,   0f,  1f,  0f,

            // Bottom face (Y = -0.5)
            -0.5f, -0.5f, -0.5f,   0f, -1f,  0f,
             0.5f, -0.5f,  0.5f,   0f, -1f,  0f,
            -0.5f, -0.5f,  0.5f,   0f, -1f,  0f,
            -0.5f, -0.5f, -0.5f,   0f, -1f,  0f,
             0.5f, -0.5f, -0.5f,   0f, -1f,  0f,
             0.5f, -0.5f,  0.5f,   0f, -1f,  0f,

            // Right face (X = 0.5)
             0.5f, -0.5f, -0.5f,   1f,  0f,  0f,
             0.5f,  0.5f,  0.5f,   1f,  0f,  0f,
             0.5f, -0.5f,  0.5f,   1f,  0f,  0f,
             0.5f, -0.5f, -0.5f,   1f,  0f,  0f,
             0.5f,  0.5f, -0.5f,   1f,  0f,  0f,
             0.5f,  0.5f,  0.5f,   1f,  0f,  0f,

            // Left face (X = -0.5)
            -0.5f, -0.5f, -0.5f,  -1f,  0f,  0f,
            -0.5f, -0.5f,  0.5f,  -1f,  0f,  0f,
            -0.5f,  0.5f,  0.5f,  -1f,  0f,  0f,
            -0.5f, -0.5f, -0.5f,  -1f,  0f,  0f,
            -0.5f,  0.5f,  0.5f,  -1f,  0f,  0f,
            -0.5f,  0.5f, -0.5f,  -1f,  0f,  0f
        )
        val vertexCount = vertices.size / 6
        return Pair(createFloatBuffer(vertices), vertexCount)
    }

    // Ground Grid plane with coordinate grid lines
    fun createGridBuffer(size: Float = 200f, step: Float = 5f): Pair<FloatBuffer, Int> {
        val lines = ArrayList<Float>()
        var coord = -size
        while (coord <= size) {
            // Line parallel to Z
            lines.add(coord); lines.add(0f); lines.add(-size)
            lines.add(coord); lines.add(0f); lines.add(size)

            // Line parallel to X
            lines.add(-size); lines.add(0f); lines.add(coord)
            lines.add(size);  lines.add(0f); lines.add(coord)

            coord += step
        }
        val floatArray = lines.toFloatArray()
        val vertexCount = floatArray.size / 3
        return Pair(createFloatBuffer(floatArray), vertexCount)
    }

    // Cylinder / Pylon for slalom flags and motors
    fun createCylinderBuffer(segments: Int = 16): Pair<FloatBuffer, Int> {
        val list = ArrayList<Float>()
        val step = (2.0 * Math.PI / segments).toFloat()

        for (i in 0 until segments) {
            val a1 = i * step
            val a2 = (i + 1) * step

            val x1 = cos(a1) * 0.5f
            val z1 = sin(a1) * 0.5f
            val x2 = cos(a2) * 0.5f
            val z2 = sin(a2) * 0.5f

            // Triangle 1
            list.add(x1); list.add(-0.5f); list.add(z1); list.add(x1); list.add(0f); list.add(z1)
            list.add(x2); list.add(-0.5f); list.add(z2); list.add(x2); list.add(0f); list.add(z2)
            list.add(x2); list.add(0.5f);  list.add(z2); list.add(x2); list.add(0f); list.add(z2)

            // Triangle 2
            list.add(x1); list.add(-0.5f); list.add(z1); list.add(x1); list.add(0f); list.add(z1)
            list.add(x2); list.add(0.5f);  list.add(z2); list.add(x2); list.add(0f); list.add(z2)
            list.add(x1); list.add(0.5f);  list.add(z1); list.add(x1); list.add(0f); list.add(z1)
        }

        val array = list.toFloatArray()
        return Pair(createFloatBuffer(array), array.size / 6)
    }
}
