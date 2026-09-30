package com.jvn.villagerretaliation.client.settlement;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Predicate;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SettlementSelectionGizmo {
    static final RenderType LINES = RenderType.create("settlement_gizmo", DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 1536, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.0)))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    public enum Mode { TRANSFORM, RESIZE }
    public record Ray(Vec3 origin, Vec3 direction) {
        public Ray {
            direction = direction.normalize();
        }
        Vec3 end() {
            return this.origin.add(this.direction.scale(512));
        }
    }
    public record Change(AABB bounds, int turns) {
    }
    private record Handle(Direction direction, boolean rotation) {
    }

    private final boolean movable;
    private final boolean resizable;
    private final boolean rotatable;
    private final Predicate<AABB> allowed;
    private AABB bounds;
    private AABB dragBounds;
    private Mode mode = Mode.TRANSFORM;
    private Handle hovered;
    private Handle active;
    private double scale = 2;
    private double dragStart;
    private int lastTurns;

    public SettlementSelectionGizmo(AABB bounds, boolean movable, boolean resizable, boolean rotatable, Predicate<AABB> allowed) {
        this.movable = movable;
        this.bounds = bounds;
        this.resizable = resizable;
        this.rotatable = rotatable;
        this.allowed = allowed;
        this.mode = !movable && resizable ? Mode.RESIZE : Mode.TRANSFORM;
    }

    public AABB bounds() {
        return this.bounds;
    }

    public void bounds(AABB bounds) {
        if (this.active == null) this.bounds = bounds;
    }

    public boolean dragging() {
        return this.active != null;
    }

    public Mode mode() {
        return this.mode;
    }

    public void mode(Mode mode) {
        if (mode == Mode.RESIZE && !this.resizable || mode == Mode.TRANSFORM && !this.movable && !this.rotatable) return;
        this.mode = mode;
        end();
    }

    public void scale(double scale) {
        if (!dragging()) this.scale = Math.clamp(scale, 1.2, 8);
    }

    private Vec3 pivot() {
        return new Vec3(this.bounds.getCenter().x, this.bounds.minY + 0.5, this.bounds.getCenter().z);
    }

    private Vec3 position(Direction direction) {
        if (this.mode == Mode.TRANSFORM) return pivot().add(Vec3.atLowerCornerOf(direction.getNormal()).scale(this.scale));
        var center = this.bounds.getCenter();
        return switch (direction) {
            case WEST -> new Vec3(this.bounds.minX, center.y, center.z);
            case EAST -> new Vec3(this.bounds.maxX, center.y, center.z);
            case DOWN -> new Vec3(center.x, this.bounds.minY, center.z);
            case UP -> new Vec3(center.x, this.bounds.maxY, center.z);
            case NORTH -> new Vec3(center.x, center.y, this.bounds.minZ);
            case SOUTH -> new Vec3(center.x, center.y, this.bounds.maxZ);
        };
    }

    private Direction[] directions() {
        return this.mode == Mode.RESIZE ? Direction.values() : this.movable
                ? new Direction[]{Direction.EAST, Direction.UP, Direction.SOUTH} : new Direction[0];
    }

    public boolean hover(Ray ray) {
        this.hovered = null;
        double distance = Double.MAX_VALUE;
        var directions = directions();
        for (var direction : directions) {
            var position = position(direction);
            var hit = new AABB(position, position).inflate(this.scale * 0.22).clip(ray.origin(), ray.end());
            if (hit.isPresent() && hit.get().distanceToSqr(ray.origin()) < distance) {
                distance = hit.get().distanceToSqr(ray.origin());
                this.hovered = new Handle(direction, false);
            }
        }
        if (this.mode == Mode.TRANSFORM && this.rotatable) {
            var point = planePoint(ray, pivot().y);
            if (point != null && Math.abs(point.subtract(pivot()).horizontalDistance() - this.scale * 1.4) < this.scale * 0.18
                    && point.distanceToSqr(ray.origin()) < distance) this.hovered = new Handle(Direction.UP, true);
        }
        return this.hovered != null;
    }

    public boolean begin(Ray ray) {
        if (!hover(ray)) return false;
        this.active = this.hovered;
        this.dragBounds = this.bounds;
        this.dragStart = this.active.rotation() ? angle(ray) : parameter(ray, this.active.direction().getAxis(), pivot());
        this.lastTurns = 0;
        return true;
    }

    public Optional<Change> drag(Ray ray) {
        if (this.active == null) return Optional.empty();
        if (this.active.rotation()) {
            double angle = angle(ray) - this.dragStart;
            angle = Math.atan2(Math.sin(angle), Math.cos(angle));
            int turns = (int) Math.round(angle / (Math.PI / 2));
            int delta = turns - this.lastTurns;
            this.lastTurns = turns;
            return delta == 0 ? Optional.empty() : Optional.of(new Change(this.bounds, delta));
        }
        var axis = this.active.direction().getAxis();
        var center = new Vec3(this.dragBounds.getCenter().x, this.dragBounds.minY + 0.5, this.dragBounds.getCenter().z);
        int delta = (int) Math.round(parameter(ray, axis, center) - this.dragStart);
        var next = this.mode == Mode.TRANSFORM ? this.dragBounds.move(axis == Direction.Axis.X ? delta : 0,
                axis == Direction.Axis.Y ? delta : 0, axis == Direction.Axis.Z ? delta : 0) : resize(this.dragBounds, this.active.direction(), delta);
        if (next.equals(this.bounds) || !this.allowed.test(next)) return Optional.empty();
        this.bounds = next;
        return Optional.of(new Change(next, 0));
    }

    public static AABB resize(AABB box, Direction face, int delta) {
        return switch (face) {
            case WEST -> new AABB(Math.min(box.maxX - 1, box.minX + delta), box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            case EAST -> new AABB(box.minX, box.minY, box.minZ, Math.max(box.minX + 1, box.maxX + delta), box.maxY, box.maxZ);
            case DOWN -> new AABB(box.minX, Math.min(box.maxY - 1, box.minY + delta), box.minZ, box.maxX, box.maxY, box.maxZ);
            case UP -> new AABB(box.minX, box.minY, box.minZ, box.maxX, Math.max(box.minY + 1, box.maxY + delta), box.maxZ);
            case NORTH -> new AABB(box.minX, box.minY, Math.min(box.maxZ - 1, box.minZ + delta), box.maxX, box.maxY, box.maxZ);
            case SOUTH -> new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, Math.max(box.minZ + 1, box.maxZ + delta));
        };
    }

    private static double parameter(Ray ray, Direction.Axis axis, Vec3 center) {
        var vector = switch (axis) { case X -> new Vec3(1, 0, 0); case Y -> new Vec3(0, 1, 0); case Z -> new Vec3(0, 0, 1); };
        var offset = ray.origin().subtract(center);
        double dot = ray.direction().dot(vector);
        return (offset.dot(vector) - dot * offset.dot(ray.direction())) / Math.max(0.0001, 1 - dot * dot);
    }

    private static Vec3 planePoint(Ray ray, double height) {
        if (Math.abs(ray.direction().y) < 0.0001) return null;
        double distance = (height - ray.origin().y) / ray.direction().y;
        return distance < 0 ? null : ray.origin().add(ray.direction().scale(distance));
    }

    private double angle(Ray ray) {
        var point = planePoint(ray, pivot().y);
        return point == null ? this.dragStart : Math.atan2(point.z - pivot().z, point.x - pivot().x);
    }

    public void end() {
        this.active = null;
        this.hovered = null;
    }

    public void render(PoseStack pose, VertexConsumer lines, boolean valid) {
        LevelRenderer.renderLineBox(pose, lines, this.bounds, valid ? 0.62F : 0.85F, valid ? 0.72F : 0.36F, valid ? 0.78F : 0.32F, 0.65F);
        var directions = directions();
        for (var direction : directions) {
            var handle = new Handle(direction, false);
            boolean highlighted = handle.equals(this.active) || handle.equals(this.hovered);
            float red = direction.getAxis() == Direction.Axis.X ? 0.78F : 0.4F;
            float green = direction.getAxis() == Direction.Axis.Y ? 0.75F : 0.45F;
            float blue = direction.getAxis() == Direction.Axis.Z ? 0.85F : 0.4F;
            var end = position(direction);
            double radius = this.scale * (highlighted ? 0.11 : 0.085);
            if (this.mode == Mode.TRANSFORM) {
                line(pose, lines, pivot(), end, red, green, blue, highlighted ? 1 : 0.7F);
                var axis = Vec3.atLowerCornerOf(direction.getNormal());
                var side = direction == Direction.UP ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                line(pose, lines, end, end.subtract(axis.scale(radius * 3)).add(side.scale(radius)), red, green, blue, 1);
                line(pose, lines, end, end.subtract(axis.scale(radius * 3)).subtract(side.scale(radius)), red, green, blue, 1);
            } else {
                LevelRenderer.renderLineBox(pose, lines, new AABB(end, end).inflate(radius), red, green, blue, highlighted ? 1 : 0.7F);
            }
        }
        if (this.mode == Mode.TRANSFORM && this.rotatable) {
            boolean highlighted = this.active != null && this.active.rotation() || this.hovered != null && this.hovered.rotation();
            double radius = this.scale * 1.4;
            for (int index = 0; index < 32; index++) {
                double a = index * Math.PI / 16, b = (index + 1) * Math.PI / 16;
                line(pose, lines, pivot().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                        pivot().add(Math.cos(b) * radius, 0, Math.sin(b) * radius), 0.7F, 0.73F, 0.65F, highlighted ? 1 : 0.6F);
            }
        }
    }

    static void line(PoseStack pose, VertexConsumer lines, Vec3 start, Vec3 end, float red, float green, float blue, float alpha) {
        var normal = end.subtract(start).normalize();
        lines.addVertex(pose.last().pose(), (float) start.x, (float) start.y, (float) start.z).setColor(red, green, blue, alpha)
                .setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
        lines.addVertex(pose.last().pose(), (float) end.x, (float) end.y, (float) end.z).setColor(red, green, blue, alpha)
                .setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
