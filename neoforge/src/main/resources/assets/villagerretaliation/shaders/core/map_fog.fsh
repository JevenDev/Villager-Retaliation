#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform vec2 Viewport;
uniform vec2 WorldSpan;
uniform vec4 PageX;
uniform vec4 PageZ;
uniform float FirstLevel;
uniform vec2 CloudOrigin;
uniform float CloudPeriod;
uniform vec3 CloudDriftX;
uniform vec3 CloudDriftZ;
uniform vec3 Lighting;
in vec2 screenPos;
out vec4 fragColor;

ivec2 terrainPixel(vec2 plane) {
    for (int level = int(FirstLevel); level < 4; level++) {
        int stride = 1 << level;
        vec2 local = plane - vec2(PageX[level], PageZ[level]);
        ivec2 tile = ivec2(floor(local / float(8 * stride)));
        if (any(lessThan(tile, ivec2(0))) || any(greaterThanEqual(tile, ivec2(64)))) continue;
        vec4 bytes = floor(texelFetch(Sampler3, tile + ivec2(0, level * 64), 0) * 255.0 + 0.5);
        int slot = int(bytes.r) + int(bytes.g) * 256 - 1;
        if (slot < 0) continue;
        if (slot == 65534) return ivec2(-1);
        ivec2 cell = ivec2(floor(mod(local, float(8 * stride)) / float(stride)));
        ivec2 pixel = ivec2(slot % 128, slot / 128) * 8 + cell;
        ivec2 subcell = ivec2(floor(mod(local, float(stride))));
        int bit = subcell.x + subcell.y * stride;
        vec4 mask = floor(texelFetch(Sampler2, ivec2(pixel.x * 2 + bit / 32, pixel.y), 0) * 255.0 + 0.5);
        int value = int(mask[(bit % 32) / 8]);
        if ((value & (1 << (bit % 8))) == 0) return ivec2(-1);
        return pixel;
    }
    return ivec2(-1);
}

ivec2 cloudPixel(ivec2 pixel, ivec2 size) {
    return ivec2(mod(vec2(pixel), vec2(size)));
}

float cloudAlpha(ivec2 pixel, ivec2 size) {
    return texelFetch(Sampler1, cloudPixel(pixel, size), 0).a;
}

float silhouette(vec2 position, ivec2 size) {
    ivec2 base = ivec2(floor(position - 0.5));
    vec2 edge = clamp(fwidth(position) * 0.5, vec2(0.001), vec2(0.5));
    vec2 blend = smoothstep(0.5 - edge, 0.5 + edge, fract(position - 0.5));
    return mix(mix(cloudAlpha(base, size), cloudAlpha(base + ivec2(1, 0), size), blend.x),
            mix(cloudAlpha(base + ivec2(0, 1), size), cloudAlpha(base + ivec2(1, 1), size), blend.x), blend.y);
}

float cloudAt(vec2 plane, int layer) {
    float level = float(layer);
    float period = CloudPeriod * exp2(level);
    ivec2 size = textureSize(Sampler1, 0);
    vec2 worldPerPixel = vec2(period) / vec2(size);
    vec2 drift = vec2(CloudDriftX[layer], CloudDriftZ[layer]);
    vec2 shift = vec2(level * 73.0, level * 131.0) - drift;
    vec2 position = (CloudOrigin + plane + shift) / worldPerPixel;
    float opacity = silhouette(position, size);
    if (opacity <= 0.0) return 0.0;
    ivec2 pixel = ivec2(floor(position));
    ivec2 wrapped = cloudPixel(pixel, size);
    vec4 extentBytes = floor(texelFetch(Sampler4, wrapped + ivec2(0, size.y), 0) * 255.0 + 0.5);
    vec2 extent = vec2(extentBytes.r + extentBytes.g * 256.0, extentBytes.b + extentBytes.a * 256.0) * worldPerPixel * 0.65;
    float sizeOnScreen = max(extent.x, extent.y) * Viewport.x / WorldSpan.x;
    return opacity * smoothstep(3.0, 12.0, sizeOnScreen);
}

void main() {
    vec2 plane = (screenPos / Viewport - 0.5) * WorldSpan;
    ivec2 pixel = terrainPixel(plane);
    // discovery always wins over cloud motion
    if (pixel.x >= 0) {
        vec4 terrain = texelFetch(Sampler0, pixel, 0);
        vec3 light = mix(Lighting, max(Lighting, vec3(1.0, 0.9, 0.75)), terrain.a * terrain.a);
        fragColor = vec4(terrain.rgb * light, 1.0);
        return;
    }
    vec3 color = vec3(0.0);
    float remaining = 1.0;
    for (int layer = 0; layer < 3; layer++) {
        float opacity = cloudAt(plane, layer);
        float shade = 1.0 - float(layer) * 0.05;
        color += vec3(shade) * Lighting * opacity * remaining;
        remaining *= 1.0 - opacity;
    }
    fragColor = vec4(color + Lighting * 0.86 * remaining, 1.0);
}
