package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.MapView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

final class MapMarkerLayout {
    record Group(int x, int y, List<MapDestination> members, Sprite sprite) {
        Group(int x, int y, List<MapDestination> members) { this(x, y, members, null); }
    }
    record Sprite(double x, double y, double size) {
        Box bounds() { return new Box((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + size), (int) Math.ceil(y + size)); }
    }
    record Label(MapDestination destination, int x, int y, int width) { }
    record Box(int left, int top, int right, int bottom) {
        boolean overlaps(Box other) { return left < other.right && right > other.left && top < other.bottom && bottom > other.top; }
    }

    static boolean bottomAnchored(MapDestination destination) {
        return destination.kind() == MapDestination.Kind.VILLAGE
                || destination.kind() == MapDestination.Kind.LOCATION && destination.icon() != null && destination.icon().mapSprite();
    }

    static Sprite sprite(MapDestination destination, MapView view) {
        if (!bottomAnchored(destination)) return null;
        int stride = 1 << view.level();
        double x = Math.floor(destination.x() / stride) * stride, z = Math.floor(destination.z() / stride) * stride;
        return new Sprite(view.screenX(x - 8 * stride), view.screenY(z - 16 * stride), 16 * stride * view.scale());
    }

    static Box bounds(Group group) {
        return group.members().size() > 1 ? new Box(group.x() - 9, group.y() - 8, group.x() + 10, group.y() + 9)
                : group.sprite() != null ? group.sprite().bounds() : new Box(group.x() - 8, group.y() - 8, group.x() + 8, group.y() + 8);
    }

    static boolean contains(Group group, double x, double y) {
        if (group.members().size() > 1) return Math.abs(group.x() - x) <= 10 && Math.abs(group.y() - y) <= 10;
        var box = bounds(group);
        return x >= box.left() - 2 && x <= box.right() + 2 && y >= box.top() - 2 && y <= box.bottom() + 2;
    }

    static List<Group> groups(List<MapDestination> destinations, MapView view, String selected) {
        int spacing = view.scale() < 1.25 ? 24 : 16;
        var groups = new ArrayList<ArrayList<MapDestination>>();
        var anchors = new ArrayList<int[]>();
        Map<Long, List<Integer>> cells = new HashMap<>();
        for (var destination : destinations) {
            var sprite = sprite(destination, view);
            int x = (int) (sprite == null ? view.screenX(destination.x()) : sprite.x() + sprite.size() / 2);
            int y = (int) (sprite == null ? view.screenY(destination.z()) : sprite.y() + sprite.size());
            var box = sprite == null ? new Box(x - 8, y - 8, x + 8, y + 8) : sprite.bounds();
            if (box.right() < -4 || box.bottom() < -4 || box.left() > view.width() + 4 || box.top() > view.height() + 4) continue;
            boolean priority = destination.key().equals(selected);
            int cx = Math.floorDiv(x, spacing), cy = Math.floorDiv(y, spacing), match = -1;
            if (!priority) for (int dz = -1; dz <= 1 && match < 0; dz++) for (int dx = -1; dx <= 1 && match < 0; dx++) {
                for (int index : cells.getOrDefault(key(cx + dx, cy + dz), List.of())) {
                    var anchor = anchors.get(index);
                    if (Math.hypot(anchor[0] - x, anchor[1] - y) < spacing) { match = index; break; }
                }
            }
            if (match < 0) {
                match = groups.size(); groups.add(new ArrayList<>()); anchors.add(new int[]{x, y});
                if (!priority) cells.computeIfAbsent(key(cx, cy), ignored -> new ArrayList<>()).add(match);
            }
            groups.get(match).add(destination);
        }
        var result = new ArrayList<Group>();
        for (int i = 0; i < groups.size(); i++) result.add(new Group(anchors.get(i)[0], anchors.get(i)[1], List.copyOf(groups.get(i)), sprite(groups.get(i).getFirst(), view)));
        result.sort(Comparator.comparingInt(group -> group.members().getFirst().key().equals(selected) ? 1 : 0));
        return result;
    }

    static List<Label> labels(List<Group> groups, MapView view, String selected, double mouseX, double mouseY,
                              boolean villageNames, ToIntFunction<String> width, int lineHeight, List<Box> reserved) {
        var singles = groups.stream().filter(group -> group.members().size() == 1).sorted(Comparator.comparingInt(
                (Group group) -> priority(group, selected, mouseX, mouseY)).reversed()).toList();
        var occupied = new ArrayList<>(reserved);
        for (var group : groups) {
            var box = bounds(group);
            occupied.add(new Box(box.left() - 1, box.top() - 1, box.right() + 1, box.bottom() + 1));
        }
        var labels = new ArrayList<Label>();
        for (var group : singles) {
            var destination = group.members().getFirst();
            int priority = priority(group, selected, mouseX, mouseY);
            if (destination.kind() == MapDestination.Kind.VILLAGE && !villageNames && priority < 100) continue;
            if (priority < 100 && (!destination.showName() || view.scale() < .75)) continue;
            int textWidth = width.applyAsInt(destination.label());
            var icon = bounds(group);
            int middle = (icon.top() + icon.bottom()) / 2 - lineHeight / 2;
            int[][] positions = {{group.x() - textWidth / 2, icon.bottom() + (bottomAnchored(destination) ? 4 : 2)},
                    {group.x() - textWidth / 2, icon.top() - lineHeight - 3},
                    {icon.right() + 4, middle}, {icon.left() - textWidth - 4, middle}};
            for (var position : positions) {
                var box = new Box(position[0] - 2, position[1] - 1, position[0] + textWidth + 2, position[1] + lineHeight + 1);
                if (box.left() < 2 || box.top() < 2 || box.right() > view.width() - 2 || box.bottom() > view.height() - 2
                        || occupied.stream().anyMatch(box::overlaps)) continue;
                occupied.add(box); labels.add(new Label(destination, position[0], position[1], textWidth)); break;
            }
        }
        return labels;
    }

    private static int priority(Group group, String selected, double mouseX, double mouseY) {
        var destination = group.members().getFirst();
        if (destination.key().equals(selected)) return 300;
        if (contains(group, mouseX, mouseY)) return 200;
        return destination.kind() == MapDestination.Kind.QUEST ? 20 : destination.kind() == MapDestination.Kind.LOCATION ? 10 : 0;
    }
    private static long key(int x, int y) { return (long) x << 32 | y & 0xffffffffL; }
}
