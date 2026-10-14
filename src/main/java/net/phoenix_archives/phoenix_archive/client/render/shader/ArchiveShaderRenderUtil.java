package net.phoenix_archives.phoenix_archive.client.render.shader;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

public final class ArchiveShaderRenderUtil {

    private ArchiveShaderRenderUtil() {}

    public static void drawDynamicShaderQuad(GuiGraphics g, ShaderInstance shader, int x, int y, int w, int h,
                                             float timeSeconds) {
        if (shader == null || w <= 0 || h <= 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, MissingTextureAtlasSprite.getLocation());
        RenderSystem.setShader(() -> shader);

        if (shader.safeGetUniform("iTime") != null) {
            shader.safeGetUniform("iTime").set(timeSeconds);
        }
        if (shader.safeGetUniform("iResolution") != null) {
            shader.safeGetUniform("iResolution").set((float) w, (float) h, 1f);
        }

        PoseStack.Pose pose = g.pose().last();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.addVertex(pose.pose(), x, y + h, 0).setUv(0, 1);
        buf.addVertex(pose.pose(), x + w, y + h, 0).setUv(1, 1);
        buf.addVertex(pose.pose(), x + w, y, 0).setUv(1, 0);
        buf.addVertex(pose.pose(), x, y, 0).setUv(0, 0);

        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.disableBlend();
    }

    public static float wrappedSeconds(long animTick) {
        return (animTick % 3_600_000L) / 1000f;
    }
}
