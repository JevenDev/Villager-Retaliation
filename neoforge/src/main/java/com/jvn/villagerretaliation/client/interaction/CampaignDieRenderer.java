package com.jvn.villagerretaliation.client.interaction;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CampaignDieRenderer {
    private CampaignDieRenderer() { }

    public static boolean available() { return GameRenderer.getPositionColorShader() != null; }

    public static boolean render(GuiGraphics graphics, Font font, float x, float y, float radius, int die, float progress) {
        return render(graphics, font, x, y, radius, die, progress, GameRenderer.getPositionColorShader());
    }

    static boolean render(GuiGraphics graphics, Font font, float x, float y, float radius, int die, float progress,
            net.minecraft.client.renderer.ShaderInstance shader) {
        if (shader == null) return false;
        var faces = CampaignDieGeometry.frame(die, progress).stream().filter(face -> face.normal().z() > 0.001F).toList();
        graphics.flush();
        var pose = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        RenderSystem.disableCull();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (var face : faces) {
            float light = 0.62F + 0.38F * Math.max(0, new Vector3f(-0.4F, 0.5F, 0.8F).normalize().dot(face.normal().vector()));
            int color = 0xFF000000 | (int) (225 * light) << 16 | (int) (202 * light) << 8 | (int) (154 * light);
            vertex(buffer, pose, x, y, radius, face.a(), color);
            vertex(buffer, pose, x, y, radius, face.c(), color);
            vertex(buffer, pose, x, y, radius, face.b(), color);
            edge(buffer, pose, x, y, radius, face.a(), face.b());
            edge(buffer, pose, x, y, radius, face.b(), face.c());
            edge(buffer, pose, x, y, radius, face.c(), face.a());
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableCull();
        for (var face : faces) {
            if (face.normal().z() < 0.15F) continue;
            String label = Integer.toString(face.number());
            var right = face.b().vector().sub(face.a().vector());
            float size = right.length() * radius * 0.48F / Math.max(font.width(label), font.lineHeight * 1.5F);
            right.normalize();
            var up = face.normal().vector().cross(right).normalize();
            var basis = new Matrix4f().m00(right.x * size).m01(-right.y * size).m10(-up.x * size).m11(up.y * size);
            var center = face.center();
            graphics.pose().pushPose();
            graphics.pose().translate(x + center.x() * radius, y - center.y() * radius, 0);
            graphics.pose().last().pose().mul(basis);
            graphics.drawString(font, label, -font.width(label) / 2, -font.lineHeight / 2, 0xFF211D15, false);
            graphics.pose().popPose();
        }
        graphics.flush();
        RenderSystem.disableBlend();
        return true;
    }

    public static void renderFallback(GuiGraphics graphics, Font font, float x, float y, int die) {
        String text = Integer.toString(die);
        graphics.drawString(font, text, Math.round(x - font.width(text) / 2.0F), Math.round(y - font.lineHeight / 2.0F), 0xFFFFFFFF, true);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, float x, float y, float radius, CampaignDieGeometry.Point point, int color) {
        buffer.addVertex(pose, x + point.x() * radius, y - point.y() * radius, 0).setColor(color);
    }

    private static void edge(BufferBuilder buffer, Matrix4f pose, float x, float y, float radius, CampaignDieGeometry.Point a, CampaignDieGeometry.Point b) {
        float ax = x + a.x() * radius; float ay = y - a.y() * radius;
        float bx = x + b.x() * radius; float by = y - b.y() * radius;
        float length = (float) Math.hypot(bx - ax, by - ay);
        if (length < 0.001F) return;
        float dx = -(by - ay) / length * 0.3F; float dy = (bx - ax) / length * 0.3F;
        int color = 0xFF403725;
        buffer.addVertex(pose, ax - dx, ay - dy, 0).setColor(color);
        buffer.addVertex(pose, bx - dx, by - dy, 0).setColor(color);
        buffer.addVertex(pose, bx + dx, by + dy, 0).setColor(color);
        buffer.addVertex(pose, ax - dx, ay - dy, 0).setColor(color);
        buffer.addVertex(pose, bx + dx, by + dy, 0).setColor(color);
        buffer.addVertex(pose, ax + dx, ay + dy, 0).setColor(color);
    }
}
