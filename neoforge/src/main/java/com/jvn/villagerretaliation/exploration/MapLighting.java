package com.jvn.villagerretaliation.exploration;

public record MapLighting(float red, float green, float blue) {
    public static final MapLighting DAY = new MapLighting(1, 1, 1);

    public static MapLighting at(double timeOfDay, boolean skyLight) {
        if (!skyLight) return DAY;
        double sun = Math.cos(timeOfDay * Math.PI * 2);
        float daylight = smooth(-.18, .25, sun);
        float twilight = (1 - smooth(0, .4, Math.abs(sun))) * .55f;
        return new MapLighting(
                mix(mix(.38f, 1, daylight), 1, twilight),
                mix(mix(.46f, 1, daylight), .72f, twilight),
                mix(mix(.66f, 1, daylight), .46f, twilight));
    }

    public int background() {
        return 0xff000000 | Math.round(0x21 * red) << 16 | Math.round(0x23 * green) << 8 | Math.round(0x28 * blue);
    }

    private static float smooth(double start, double end, double value) {
        double t = Math.clamp((value - start) / (end - start), 0, 1);
        return (float)(t * t * (3 - 2 * t));
    }

    private static float mix(float from, float to, float amount) { return from + (to - from) * amount; }
}
