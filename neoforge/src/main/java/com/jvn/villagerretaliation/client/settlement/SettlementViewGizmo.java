package com.jvn.villagerretaliation.client.settlement;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

final class SettlementViewGizmo {
    private static final RenderType GEOMETRY = RenderType.create("settlement_gizmo",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    private static final Vec3 LIGHT = new Vec3(-0.3D, 1.0D, 0.6D).normalize();
    private Drag drag;
    private boolean limited;
    private SettlementViewBounds current;

    static Ray ray(Camera camera, int width, int height, double x, double y) {
        double tangent = Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D));
        Vec3 direction = new Vec3(camera.getLookVector())
                .add(new Vec3(camera.getLeftVector()).scale(-(x / width * 2.0D - 1.0D) * tangent * width / height))
                .add(new Vec3(camera.getUpVector()).scale((1.0D - y / height * 2.0D) * tangent)).normalize();
        return new Ray(camera.getPosition(), direction);
    }

    static Point project(Camera camera, int width, int height, Vec3 position) {
        Vec3 relative = position.subtract(camera.getPosition());
        double depth = relative.dot(new Vec3(camera.getLookVector()));
        if (depth < 0.1D) {
            return null;
        }
        double scale = height / (2.0D * depth * Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D)));
        return new Point(width / 2.0D - relative.dot(new Vec3(camera.getLeftVector())) * scale,
                height / 2.0D - relative.dot(new Vec3(camera.getUpVector())) * scale);
    }

    List<Target> targets(Camera camera, int width, int height, int viewportWidth, SettlementViewBounds bounds) {
        Vec3 center = bounds.box().getCenter();
        Point origin = project(camera, width, height, center);
        if (origin == null) {
            return List.of();
        }
        List<Target> targets = new ArrayList<>();
        for (Direction.Axis axis : Direction.Axis.values()) {
            double length = arrowLength(camera, width, height, center, axis, 84.0D);
            Vec3 position = center.add(vector(axis).scale(length));
            Point point = project(camera, width, height, position);
            for (int attempt = 0; point != null && overlaps(targets, point) && attempt < 4; attempt++) {
                length = arrowLength(camera, width, height, center, axis, 84.0D + (attempt + 1) * 20.0D);
                position = center.add(vector(axis).scale(length));
                point = project(camera, width, height, position);
            }
            for (int attempt = 0; point != null && !inside(point, viewportWidth, height) && attempt < 16; attempt++) {
                length *= 0.85D;
                position = center.add(vector(axis).scale(length));
                point = project(camera, width, height, position);
            }
            if (point != null && inside(point, viewportWidth, height)) {
                targets.add(new Target(new Handle(axis, null), origin, point, center, position,
                        worldUnits(camera, height, position, 1)));
            }
        }
        for (Direction face : Direction.values()) {
            Vec3 anchor = center.add(vector(face.getAxis()).scale(face.getAxisDirection().getStep()
                    * bounds.size(face.getAxis()) / 2.0D));
            Point point = project(camera, width, height, anchor);
            if (point != null && inside(point, viewportWidth, height)) {
                targets.add(new Target(new Handle(face.getAxis(), face), point, point, anchor, anchor,
                        worldUnits(camera, height, anchor, 1)));
            }
        }
        return targets;
    }

    private static boolean overlaps(List<Target> targets, Point point) {
        return targets.stream().anyMatch(target -> target.point().distance(point) < 18.0D / screenScale());
    }

    private static double arrowLength(Camera camera, int width, int height, Vec3 center, Direction.Axis axis, double pixels) {
        double depth = center.subtract(camera.getPosition()).dot(new Vec3(camera.getLookVector()));
        double along = vector(axis).dot(new Vec3(camera.getLookVector()));
        Point origin = project(camera, width, height, center);
        Point next = project(camera, width, height, center.add(vector(axis)));
        double projected = next == null ? 0 : origin.distance(next) * (depth + along);
        double desired = pixels / screenScale();
        double length = desired * depth / Math.max(projected - desired * along, 1.0D);
        return Math.min(length, depth * 0.65D);
    }

    private static double worldUnits(Camera camera, int height, Vec3 position, double pixels) {
        double depth = Math.max(0.1D, position.subtract(camera.getPosition()).dot(new Vec3(camera.getLookVector())));
        return pixels / screenScale() * 2.0D * depth * Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D)) / height;
    }

    private static double screenScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    private static boolean inside(Point point, int viewportWidth, int height) {
        return point.x() >= 8 && point.x() < viewportWidth - 8 && point.y() >= 8 && point.y() < height - 8;
    }

    Target hit(Camera camera, int width, int height, int viewportWidth, SettlementViewBounds bounds, double x, double y) {
        if (x < 0 || x >= viewportWidth || y < 0 || y >= height) {
            return null;
        }
        Point mouse = new Point(x, y);
        List<Target> targets = targets(camera, width, height, viewportWidth, bounds);
        Target point = targets.stream().filter(target -> target.point().distance(mouse) <= 11.0D / screenScale())
                .min(java.util.Comparator.comparingDouble(target -> target.point().distance(mouse))).orElse(null);
        if (point != null) {
            return point;
        }
        return targets.stream().filter(target -> target.handle().face() == null
                        && target.origin().distance(mouse) > 12.0D / screenScale()
                        && segmentDistance(target.origin(), target.point(), mouse) <= 8.0D / screenScale())
                .min(java.util.Comparator.comparingDouble(target -> segmentDistance(target.origin(), target.point(), mouse))).orElse(null);
    }

    private static double segmentDistance(Point start, Point end, Point point) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double length = dx * dx + dy * dy;
        double amount = length < 1.0E-8D ? 0 : Math.clamp(((point.x() - start.x()) * dx + (point.y() - start.y()) * dy) / length, 0, 1);
        return point.distance(new Point(start.x() + dx * amount, start.y() + dy * amount));
    }

    void begin(Target target, SettlementViewBounds bounds, Camera camera, int width, int height, double x, double y) {
        Ray ray = ray(camera, width, height, x, y);
        double scalar = coordinate(ray, target.anchor(), target.handle().axis());
        this.drag = new Drag(target.handle(), bounds, target.anchor(), scalar, x, y,
                pixels(camera, width, height, target.anchor(), target.handle().axis()), screenDrag(camera, ray, target.handle().axis()));
        this.limited = false;
        this.current = bounds;
    }

    SettlementViewBounds move(Camera camera, int width, int height, double x, double y, SettlementViewBounds.Limits limits) {
        double scalar = coordinate(ray(camera, width, height, x, y), this.drag.anchor(), this.drag.handle().axis());
        if (!this.drag.fallback() && !Double.isFinite(scalar)) {
            return this.current;
        }
        Point pixels = this.drag.pixels();
        double amount = !this.drag.fallback() ? scalar - this.drag.scalar()
                : ((x - this.drag.x()) * pixels.x() + (y - this.drag.y()) * pixels.y())
                    / (pixels.x() * pixels.x() + pixels.y() * pixels.y());
        int blocks = (int) Math.clamp(Math.round(amount), -1_000_000L, 1_000_000L);
        var handle = this.drag.handle();
        var before = this.drag.before();
        var next = handle.face() == null ? before.translate(handle.axis(), blocks, limits) : before.resize(handle.face(), blocks, limits);
        int actual = handle.face() == null || handle.face().getAxisDirection() == Direction.AxisDirection.NEGATIVE
                ? next.min().get(handle.axis()) - before.min().get(handle.axis())
                : next.max().get(handle.axis()) - before.max().get(handle.axis());
        this.limited = actual != blocks;
        this.current = next;
        return next;
    }

    static boolean screenDrag(Camera camera, Ray ray, Direction.Axis axis) {
        return Math.abs(new Vec3(camera.getLookVector()).dot(vector(axis))) > 0.98D
                || 1.0D - Math.pow(ray.direction().dot(vector(axis)), 2) < 0.02D;
    }

    static double coordinate(Ray ray, Vec3 anchor, Direction.Axis axis) {
        Vec3 direction = vector(axis);
        double dot = ray.direction().dot(direction);
        double divisor = 1.0D - dot * dot;
        if (divisor < 0.02D) {
            return Double.NaN;
        }
        Vec3 relative = ray.start().subtract(anchor);
        return (relative.dot(direction) - dot * relative.dot(ray.direction())) / divisor;
    }

    static Point pixels(Camera camera, int width, int height, Vec3 anchor, Direction.Axis axis) {
        Point start = project(camera, width, height, anchor);
        Point end = project(camera, width, height, anchor.add(vector(axis)));
        Point result = start == null || end == null ? new Point(0, 0) : new Point(end.x() - start.x(), end.y() - start.y());
        if (Math.hypot(result.x(), result.y()) < 0.5D) {
            double depth = Math.max(1.0D, anchor.subtract(camera.getPosition()).length());
            double scale = height / (2.0D * depth * Math.tan(Math.toRadians(SettlementViewClient.FOV / 2.0D)));
            return axis == Direction.Axis.X ? new Point(scale, 0) : new Point(0, -scale);
        }
        return result;
    }

    private static Vec3 vector(Direction.Axis axis) {
        return new Vec3(axis == Direction.Axis.X ? 1 : 0, axis == Direction.Axis.Y ? 1 : 0, axis == Direction.Axis.Z ? 1 : 0);
    }

    boolean dragging() {
        return this.drag != null;
    }

    boolean limited() {
        return this.limited;
    }

    SettlementViewBounds cancel() {
        SettlementViewBounds before = this.drag.before();
        end();
        return before;
    }

    void end() {
        this.drag = null;
        this.current = null;
        this.limited = false;
    }

    Component narration() {
        return this.drag == null ? Component.empty() : this.drag.handle().label();
    }

    private boolean active(Handle handle, Target hover) {
        return this.drag != null && this.drag.handle().equals(handle) || hover != null && hover.handle().equals(handle);
    }

    private static int color(Handle handle) {
        return handle.axis() == Direction.Axis.X ? 0xFFED5C5C : handle.axis() == Direction.Axis.Y ? 0xFF74D86A : 0xFF669CFF;
    }

    void renderWorld(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, int width, int height,
                     int viewportWidth, SettlementViewBounds bounds, double mouseX, double mouseY) {
        List<Target> targets = targets(camera, width, height, viewportWidth, bounds);
        if (targets.isEmpty()) {
            return;
        }
        Target hover = hit(camera, width, height, viewportWidth, bounds, mouseX, mouseY);
        VertexConsumer vertices = buffers.getBuffer(GEOMETRY);
        Vec3 center = bounds.box().getCenter();
        for (Target target : targets) {
            Handle handle = target.handle();
            boolean active = active(handle, hover);
            int color = active ? 0xFFFFD45A : color(handle);
            if (handle.face() == null) {
                Vec3 direction = vector(handle.axis());
                double head = Math.min(target.unit() * 18.0D, center.distanceTo(target.position()) * 0.35D);
                Vec3 neck = target.position().subtract(direction.scale(head));
                tube(pose, vertices, camera, center, neck, target.unit() * 2.1D, color);
                cone(pose, vertices, camera, neck, target.position(), target.unit() * 7.0D, color);
            } else {
                if (active) {
                    highlightFace(pose, vertices, camera, bounds, target, color);
                }
                block(pose, vertices, camera, target.position(), target.unit() * (active ? 6.0D : 5.0D), color);
            }
        }
        block(pose, vertices, camera, center, worldUnits(camera, height, center, 3.5D), 0xFFC8C8C8);
        buffers.endBatch(GEOMETRY);
    }

    private static void highlightFace(PoseStack pose, VertexConsumer vertices, Camera camera, SettlementViewBounds bounds,
                                      Target target, int color) {
        Vec3 normal = vector(target.handle().axis()).scale(target.handle().face().getAxisDirection().getStep());
        Vec3 right = vector(target.handle().axis() == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X);
        Vec3 up = normal.cross(right);
        double halfWidth = bounds.size(right.y == 1 ? Direction.Axis.Y : Direction.Axis.X) / 2.0D;
        double halfHeight = bounds.size(up.x != 0 ? Direction.Axis.X : up.y != 0 ? Direction.Axis.Y : Direction.Axis.Z) / 2.0D;
        Vec3 center = target.anchor();
        Vec3 a = center.subtract(right.scale(halfWidth)).subtract(up.scale(halfHeight));
        Vec3 b = center.add(right.scale(halfWidth)).subtract(up.scale(halfHeight));
        Vec3 c = center.add(right.scale(halfWidth)).add(up.scale(halfHeight));
        Vec3 d = center.subtract(right.scale(halfWidth)).add(up.scale(halfHeight));
        quad(pose, vertices, a, b, c, d, color & 0xFFFFFF | 0x28000000);
        double radius = target.unit() * 0.8D;
        tube(pose, vertices, camera, a, b, radius, color);
        tube(pose, vertices, camera, b, c, radius, color);
        tube(pose, vertices, camera, c, d, radius, color);
        tube(pose, vertices, camera, d, a, radius, color);
    }

    private static void block(PoseStack pose, VertexConsumer vertices, Camera camera, Vec3 center, double radius, int color) {
        for (Direction face : Direction.values()) {
            Vec3 normal = vector(face.getAxis()).scale(face.getAxisDirection().getStep());
            Vec3 right = vector(face.getAxis() == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X).scale(radius);
            Vec3 up = normal.cross(right);
            Vec3 outer = center.add(normal.scale(radius));
            Vec3 inner = center.add(normal.scale(radius * 1.002D));
            Vec3 a = outer.subtract(right).subtract(up);
            Vec3 b = outer.add(right).subtract(up);
            Vec3 c = outer.add(right).add(up);
            Vec3 d = outer.subtract(right).add(up);
            right = right.scale(0.76D);
            up = up.scale(0.76D);
            Vec3 e = inner.subtract(right).subtract(up);
            Vec3 f = inner.add(right).subtract(up);
            Vec3 g = inner.add(right).add(up);
            Vec3 h = inner.subtract(right).add(up);
            surface(pose, vertices, camera, a, b, f, e, shade(color, 0.42D));
            surface(pose, vertices, camera, b, c, g, f, shade(color, 0.42D));
            surface(pose, vertices, camera, c, d, h, g, shade(color, 0.42D));
            surface(pose, vertices, camera, d, a, e, h, shade(color, 0.42D));
            surface(pose, vertices, camera, e, f, g, h, color);
        }
    }

    private static void tube(PoseStack pose, VertexConsumer vertices, Camera camera, Vec3 start, Vec3 end, double radius, int color) {
        Vec3 direction = end.subtract(start).normalize();
        if (direction.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 right = direction.cross(Math.abs(direction.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = direction.cross(right);
        for (int side = 0; side < 8; side++) {
            double angle = side * Math.PI / 4.0D;
            double next = (side + 1) * Math.PI / 4.0D;
            Vec3 a = right.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
            Vec3 b = right.scale(Math.cos(next) * radius).add(up.scale(Math.sin(next) * radius));
            surface(pose, vertices, camera, start.add(a), start.add(b), end.add(b), end.add(a), color);
            surface(pose, vertices, camera, start, start.add(b), start.add(a), start.add(a), color);
            surface(pose, vertices, camera, end, end.add(a), end.add(b), end.add(b), color);
        }
    }

    private static void cone(PoseStack pose, VertexConsumer vertices, Camera camera, Vec3 base, Vec3 tip, double radius, int color) {
        Vec3 direction = tip.subtract(base).normalize();
        Vec3 right = direction.cross(Math.abs(direction.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = direction.cross(right);
        for (int side = 0; side < 8; side++) {
            double angle = side * Math.PI / 4.0D;
            double next = (side + 1) * Math.PI / 4.0D;
            Vec3 a = base.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
            Vec3 b = base.add(right.scale(Math.cos(next) * radius)).add(up.scale(Math.sin(next) * radius));
            surface(pose, vertices, camera, a, b, tip, tip, color);
            surface(pose, vertices, camera, base, b, a, a, color);
        }
    }

    private static void surface(PoseStack pose, VertexConsumer vertices, Camera camera, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        Vec3 normal = b.subtract(a).cross(c.subtract(a)).normalize();
        if (normal.dot(camera.getPosition().subtract(a)) <= 0) {
            return;
        }
        quad(pose, vertices, a, b, c, d, shade(color, 0.65D + Math.max(0, normal.dot(LIGHT)) * 0.35D));
    }

    private static int shade(int color, double light) {
        int red = (int) ((color >> 16 & 255) * light);
        int green = (int) ((color >> 8 & 255) * light);
        int blue = (int) ((color & 255) * light);
        return color & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void quad(PoseStack pose, VertexConsumer vertices, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        vertices.addVertex(pose.last(), (float) a.x, (float) a.y, (float) a.z).setColor(color);
        vertices.addVertex(pose.last(), (float) b.x, (float) b.y, (float) b.z).setColor(color);
        vertices.addVertex(pose.last(), (float) c.x, (float) c.y, (float) c.z).setColor(color);
        vertices.addVertex(pose.last(), (float) d.x, (float) d.y, (float) d.z).setColor(color);
    }

    void render(GuiGraphics graphics, Font font, Camera camera, int width, int height, int viewportWidth,
                SettlementViewBounds bounds, double mouseX, double mouseY) {
        var hover = hit(camera, width, height, viewportWidth, bounds, mouseX, mouseY);
        float scale = (float) (2.0D / screenScale());
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        for (var target : targets(camera, width, height, viewportWidth, bounds)) {
            if (target.handle().face() == null) {
                graphics.drawString(font, target.handle().axis().name(), (int) Math.round(target.point().x() / scale) + 5,
                        (int) Math.round(target.point().y() / scale) - font.lineHeight / 2,
                        active(target.handle(), hover) ? 0xFFFFD45A : color(target.handle()));
            }
        }
        graphics.pose().popPose();
    }

    void renderTooltip(GuiGraphics graphics, Font font, Camera camera, int width, int height, int viewportWidth,
                       SettlementViewBounds bounds, double mouseX, double mouseY) {
        var hover = hit(camera, width, height, viewportWidth, bounds, mouseX, mouseY);
        if (hover != null) {
            graphics.renderTooltip(font, hover.handle().label(), (int) mouseX, (int) mouseY);
        }
    }

    record Ray(Vec3 start, Vec3 direction) {
        Vec3 end() {
            return this.start.add(this.direction.scale(256.0D));
        }
    }

    record Point(double x, double y) {
        double distance(Point other) {
            return Math.hypot(this.x - other.x, this.y - other.y);
        }
    }

    record Handle(Direction.Axis axis, Direction face) {
        Component label() {
            return Component.translatable("villagerretaliation.gui.settlement_view."
                    + (this.face == null ? "move_" + this.axis.name().toLowerCase(java.util.Locale.ROOT)
                    : "resize_" + this.face.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    record Target(Handle handle, Point origin, Point point, Vec3 anchor, Vec3 position, double unit) {
    }

    private record Drag(Handle handle, SettlementViewBounds before, Vec3 anchor, double scalar,
                        double x, double y, Point pixels, boolean fallback) {
    }
}
