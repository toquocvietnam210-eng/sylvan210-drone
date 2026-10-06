package com.example.sim.renderer

import android.opengl.GLES20
import android.util.Log

object ShaderHelper {
    private const val TAG = "ShaderHelper"

    fun compileShader(shaderType: Int, shaderSource: String): Int {
        var shaderHandle = GLES20.glCreateShader(shaderType)
        if (shaderHandle != 0) {
            GLES20.glShaderSource(shaderHandle, shaderSource)
            GLES20.glCompileShader(shaderHandle)

            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

            if (compileStatus[0] == 0) {
                Log.e(TAG, "Shader compile failed: " + GLES20.glGetShaderInfoLog(shaderHandle))
                GLES20.glDeleteShader(shaderHandle)
                shaderHandle = 0
            }
        }
        return shaderHandle
    }

    fun createAndLinkProgram(
        vertexShaderHandle: Int,
        fragmentShaderHandle: Int,
        attributes: Array<String>? = null
    ): Int {
        var programHandle = GLES20.glCreateProgram()
        if (programHandle != 0) {
            GLES20.glAttachShader(programHandle, vertexShaderHandle)
            GLES20.glAttachShader(programHandle, fragmentShaderHandle)

            attributes?.forEachIndexed { index, attribute ->
                GLES20.glBindAttribLocation(programHandle, index, attribute)
            }

            GLES20.glLinkProgram(programHandle)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0)

            if (linkStatus[0] == 0) {
                Log.e(TAG, "Program linking failed: " + GLES20.glGetProgramInfoLog(programHandle))
                GLES20.glDeleteProgram(programHandle)
                programHandle = 0
            }
        }
        return programHandle
    }
}
