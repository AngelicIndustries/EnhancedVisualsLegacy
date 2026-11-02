package com.sonicjumper.enhancedvisuals.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderLinkHelper;
import net.minecraft.client.util.JsonException;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import javax.vecmath.Matrix4f;

/** Owns every intermediate target and pass; never takes EntityRenderer's shader slot. */
final class EffectPipeline {
    private final List<Framebuffer> targets = new ArrayList<>();
    private final List<Shader> passes = new ArrayList<>();
    private final List<Shader> blurs = new ArrayList<>();
    private Framebuffer main, output;
    private Shader finish;
    private int width, height, scale;

    void render(float radius, float saturation) throws JsonException {
        int desiredScale = 1;
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer current = mc.getFramebuffer();
        // Shader.loadShader changes fixed-function state, matrices, the program and framebuffer binding.
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int filter = current.framebufferFilter;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            if (current != main || width != current.framebufferWidth || height != current.framebufferHeight || scale != desiredScale) {
                close();
                main = current;
                width = main.framebufferWidth;
                height = main.framebufferHeight;
                scale = desiredScale;
                build();
            }
            current.setFramebufferFilter(GL11.GL_LINEAR);
            for (Shader blur : blurs) blur.getShaderManager().func_147991_a("Radius").func_148090_a(radius / scale);
            finish.getShaderManager().func_147991_a("Saturation").func_148090_a(saturation);
            for (Shader pass : passes) pass.loadShader(0);
            // Never clear the shared depth buffer: only our owned targets are shader outputs.
            current.bindFramebuffer(true);
            output.framebufferRender(current.framebufferWidth, current.framebufferHeight);
        } finally {
            current.setFramebufferFilter(filter);
            OpenGlHelper.func_153161_d(program);
            current.bindFramebuffer(true);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glMatrixMode(matrixMode);
        }
    }

    private void build() throws JsonException {
        // The title screen and the first paused world frame can precede EntityRenderer.updateRenderer().
        if (ShaderLinkHelper.getStaticShaderLinkHelper() == null) ShaderLinkHelper.setNewStaticShaderLinkHelper();
        Framebuffer low = main;
        for (int step = 2; step <= scale; step *= 2) {
            Framebuffer next = target(width / step, height / step);
            pass("evlegacy_copy", low, next);
            low = next;
        }
        if (low == main) {
            low = target(width, height);
            pass("evlegacy_copy", main, low);
        }
        Framebuffer swap = target(low.framebufferWidth, low.framebufferHeight);
        for (int i = 0; i < 2; i++) {
            blur(low, swap, 1, 0);
            blur(swap, low, 0, 1);
        }
        output = target(width, height);
        finish = pass("evlegacy_finish", low, output);
    }

    private Framebuffer target(int w, int h) {
        Framebuffer target = new Framebuffer(Math.max(1, w), Math.max(1, h), false);
        targets.add(target);
        target.setFramebufferFilter(GL11.GL_LINEAR);
        return target;
    }

    private Shader pass(String name, Framebuffer in, Framebuffer out) throws JsonException {
        Shader shader = new Shader(Minecraft.getMinecraft().getResourceManager(), name, in, out);
        passes.add(shader);
        Matrix4f matrix = new Matrix4f();
        matrix.setIdentity();
        matrix.m00 = 2F / out.framebufferTextureWidth;
        matrix.m11 = -2F / out.framebufferTextureHeight;
        matrix.m22 = -0.0020001999F;
        matrix.m03 = -1;
        matrix.m13 = 1;
        matrix.m23 = -1.0001999F;
        shader.setProjectionMatrix(matrix);
        return shader;
    }

    private void blur(Framebuffer in, Framebuffer out, float x, float y) throws JsonException {
        Shader shader = pass("evlegacy_blur", in, out);
        shader.getShaderManager().func_147991_a("BlurDir").func_148087_a(x, y);
        blurs.add(shader);
    }

    void close() {
        boolean deleted = !targets.isEmpty();
        for (Shader pass : passes) pass.deleteShader();
        for (Framebuffer target : targets) target.deleteFramebuffer();
        // Deleting a framebuffer binds the window's; mid-frame, the rest of the frame would draw there and flash.
        if (deleted) Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
        passes.clear();
        blurs.clear();
        targets.clear();
        main = null;
    }
}
