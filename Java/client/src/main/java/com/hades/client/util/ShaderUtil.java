package com.hades.client.util;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public class ShaderUtil {
    private final int programID;

    public ShaderUtil(String fragmentShaderSource) {
        int program = GL20.glCreateProgram();
        try {
            int fragmentShaderID = createShader(fragmentShaderSource, GL20.GL_FRAGMENT_SHADER);
            GL20.glAttachShader(program, fragmentShaderID);
            
            // Basic pass-through vertex shader
            String vertexShaderSource = "#version 120\n" +
                    "void main() {\n" +
                    "gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
                    "gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
                    "}";
            int vertexShaderID = createShader(vertexShaderSource, GL20.GL_VERTEX_SHADER);
            GL20.glAttachShader(program, vertexShaderID);

            GL20.glLinkProgram(program);
            int status = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);
            if (status == 0) {
                HadesLogger.get().error("Shader Link Error: " + GL20.glGetProgramInfoLog(program, 1024));
            }
        } catch (Exception e) {
            HadesLogger.get().error("Failed to initialize shader", e);
        }
        this.programID = program;
    }

    public void useShader() {
        GL20.glUseProgram(programID);
    }

    public void releaseShader() {
        GL20.glUseProgram(0);
    }

    public int getUniform(String name) {
        return GL20.glGetUniformLocation(programID, name);
    }

    public void setUniformf(String name, float... args) {
        int loc = getUniform(name);
        switch (args.length) {
            case 1: GL20.glUniform1f(loc, args[0]); break;
            case 2: GL20.glUniform2f(loc, args[0], args[1]); break;
            case 3: GL20.glUniform3f(loc, args[0], args[1], args[2]); break;
            case 4: GL20.glUniform4f(loc, args[0], args[1], args[2], args[3]); break;
        }
    }

    public void setUniformi(String name, int... args) {
        int loc = getUniform(name);
        switch (args.length) {
            case 1: GL20.glUniform1i(loc, args[0]); break;
            case 2: GL20.glUniform2i(loc, args[0], args[1]); break;
            case 3: GL20.glUniform3i(loc, args[0], args[1], args[2]); break;
            case 4: GL20.glUniform4i(loc, args[0], args[1], args[2], args[3]); break;
        }
    }

    private int createShader(String source, int shaderType) {
        int shader = GL20.glCreateShader(shaderType);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            HadesLogger.get().error("Shader Compile Error: " + GL20.glGetShaderInfoLog(shader, 1024));
        }
        return shader;
    }

    public static void drawQuads(float x, float y, float width, float height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + width, y);
        GL11.glEnd();
    }
}
