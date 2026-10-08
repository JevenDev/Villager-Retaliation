package com.jvn.villagerretaliation.client.interaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CampaignDieGeometry {
    private static final List<Face> FACES = createFaces();

    private CampaignDieGeometry() { }

    public static List<Face> frame(int die, float progress) {
        if (die < 1 || die > 20 || !Float.isFinite(progress)) throw new IllegalArgumentException("Invalid die presentation");
        var selected = FACES.get(die - 1);
        var facing = new Quaternionf().rotationTo(selected.normal().vector(), new Vector3f(0, 0, 1));
        var tip = selected.c().vector().sub(selected.center().vector()).rotate(facing);
        var settled = new Quaternionf().rotateZ((float) (Math.PI / 2 - Math.atan2(tip.y, tip.x))).mul(facing);
        float remaining = 1 - Math.clamp(progress, 0, 1);
        float turn = remaining * remaining * remaining * (float) (Math.PI * 6);
        var rotation = new Quaternionf().rotateXYZ(turn * 0.83F, turn, turn * 0.31F).mul(settled);
        return FACES.stream().map(face -> new Face(face.number(), rotate(face.a(), rotation), rotate(face.b(), rotation),
                rotate(face.c(), rotation), rotate(face.normal(), rotation))).sorted(Comparator.comparingDouble(face -> face.center().z())).toList();
    }

    private static Point rotate(Point point, Quaternionf rotation) { return Point.of(point.vector().rotate(rotation)); }

    private static List<Face> createFaces() {
        float golden = (float) ((1 + Math.sqrt(5)) / 2);
        var vertices = List.of(new Vector3f(-1, golden, 0), new Vector3f(1, golden, 0), new Vector3f(-1, -golden, 0), new Vector3f(1, -golden, 0),
                new Vector3f(0, -1, golden), new Vector3f(0, 1, golden), new Vector3f(0, -1, -golden), new Vector3f(0, 1, -golden),
                new Vector3f(golden, 0, -1), new Vector3f(golden, 0, 1), new Vector3f(-golden, 0, -1), new Vector3f(-golden, 0, 1));
        var faces = new ArrayList<Face>();
        for (int a = 0; a < vertices.size(); a++) for (int b = a + 1; b < vertices.size(); b++) for (int c = b + 1; c < vertices.size(); c++) {
            var first = vertices.get(a); var second = vertices.get(b); var third = vertices.get(c);
            if (!edge(first, second) || !edge(second, third) || !edge(third, first)) continue;
            var normal = new Vector3f(second).sub(first).cross(new Vector3f(third).sub(first)).normalize();
            if (normal.dot(first) < 0) { var swap = second; second = third; third = swap; normal.negate(); }
            faces.add(new Face(faces.size() + 1, Point.of(new Vector3f(first).normalize()), Point.of(new Vector3f(second).normalize()),
                    Point.of(new Vector3f(third).normalize()), Point.of(normal)));
        }
        if (faces.size() != 20) throw new IllegalStateException("An icosahedral die requires twenty faces");
        return List.copyOf(faces);
    }

    private static boolean edge(Vector3f first, Vector3f second) { return Math.abs(first.distanceSquared(second) - 4) < 0.0001F; }

    public record Point(float x, float y, float z) {
        Vector3f vector() { return new Vector3f(x, y, z); }
        static Point of(Vector3f vector) { return new Point(vector.x, vector.y, vector.z); }
    }

    public record Face(int number, Point a, Point b, Point c, Point normal) {
        public Point center() { return new Point((a.x + b.x + c.x) / 3, (a.y + b.y + c.y) / 3, (a.z + b.z + c.z) / 3); }
    }
}
