package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.MapView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class MapRouteLine {
    private record Pixel(BlockPos position, int color) { }
    private MapRouteLine() { }
    static void render(GuiGraphics graphics, MapView view, List<Vec3> points, int color) { render(graphics, view, points, ignored -> color); }
    static void render(GuiGraphics graphics, MapView view, List<Vec3> points, IntUnaryOperator colors) {
        int stride = 1 << view.level();
        var cells = pixels(points, stride, colors);
        for (var entry : cells.entrySet()) {
            var cell = BlockPos.of(entry.getKey());
            int left = (int) Math.floor(view.screenX(cell.getX() * stride)), top = (int) Math.floor(view.screenY(cell.getZ() * stride));
            int right = Math.max(left + 1, (int) Math.ceil(view.screenX((cell.getX() + 1) * stride)));
            int bottom = Math.max(top + 1, (int) Math.ceil(view.screenY((cell.getZ() + 1) * stride)));
            if (right < 0 || bottom < 0 || left >= view.width() || top >= view.height()) continue;
            graphics.fill(left, top, right, bottom, entry.getValue());
        }
    }
    static LinkedHashMap<Long, Integer> pixels(List<Vec3> points, int stride, IntUnaryOperator colors) {
        var cells = new LinkedHashMap<Long, Integer>();
        var run = new java.util.ArrayList<Pixel>();
        BlockPos previous = null;
        for (int index = 0; index < points.size(); index++) {
            var point = points.get(index);
            if (point == null) {
                for (var pixel : run) cells.put(pixel.position().asLong(), pixel.color());
                run.clear(); previous = null; continue;
            }
            var next = new BlockPos((int) Math.floor(point.x / stride), 0, (int) Math.floor(point.z / stride));
            int color = colors.applyAsInt(index);
            if (previous == null) append(run, next, color);
            else {
                int steps = Math.max(Math.abs(next.getX() - previous.getX()), Math.abs(next.getZ() - previous.getZ()));
                for (int step = 1; step <= steps; step++) {
                    int x = (int) Math.round(previous.getX() + (next.getX() - previous.getX()) * step / (double) steps);
                    int z = (int) Math.round(previous.getZ() + (next.getZ() - previous.getZ()) * step / (double) steps);
                    append(run, new BlockPos(x, 0, z), color);
                }
            }
            previous = next;
        }
        for (var pixel : run) cells.put(pixel.position().asLong(), pixel.color());
        return cells;
    }
    private static void append(java.util.ArrayList<Pixel> run, BlockPos point, int color) {
        if (!run.isEmpty() && run.getLast().position().equals(point)) return;
        if (run.size() >= 2) {
            var before = run.get(run.size() - 2).position(); var elbow = run.getLast().position();
            // drop the elbow pixel when its neighbors already meet diagonally
            if (Math.abs(before.getX() - point.getX()) == 1 && Math.abs(before.getZ() - point.getZ()) == 1
                    && before.distManhattan(elbow) == 1 && elbow.distManhattan(point) == 1) run.removeLast();
        }
        run.add(new Pixel(point, color));
    }

}
