package com.jvn.villagerretaliation.exploration;

public record MapView(int width, int height, double x, double z, double scale) {
    public MapView {
        if (width < 1 || height < 1 || !Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("Map view bounds");
    }
    public double screenX(double worldX) { return width * .5 + (worldX - x) * scale; }
    public double screenY(double worldZ) { return height * .5 + (worldZ - z) * scale; }
    public double worldX(double pixel) { return x + (pixel - width * .5) / scale; }
    public double worldZ(double pixel) { return z + (pixel - height * .5) / scale; }
    public int level() {
        int level = 0;
        while (level < 3 && Math.max(width, height) / scale > (8 << level) * 44) level++;
        return level;
    }
}
