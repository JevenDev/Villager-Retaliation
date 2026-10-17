#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform vec2 Viewport;
uniform vec2 WorldSpan;
uniform vec4 PageX;
uniform vec4 PageZ;
uniform float FirstLevel;
uniform vec2 CloudOrigin;
uniform float CloudPeriod;
uniform vec3 Lighting;
in vec2 screenPos;
out vec4 fragColor;

vec4 terrainAt(vec2 plane) {
    for (int level = int(FirstLevel); level < 4; level++) {
        int stride = 1 << level;
        vec2 local = plane - vec2(PageX[level], PageZ[level]);
        ivec2 tile = ivec2(floor(local / float(8 * stride)));
        if (any(lessThan(tile, ivec2(0))) || any(greaterThanEqual(tile, ivec2(64)))) continue;
        vec4 bytes = floor(texelFetch(Sampler3, tile + ivec2(0, level * 64), 0) * 255.0 + 0.5);
        int slot = int(bytes.r) + int(bytes.g) * 256 - 1;
        if (slot < 0) continue;
        if (slot == 65534) return vec4(0.0, 0.0, 0.0, -1.0);
        ivec2 cell = ivec2(floor(mod(local, float(8 * stride)) / float(stride)));
        ivec2 pixel = ivec2(slot % 128, slot / 128) * 8 + cell;
        ivec2 subcell = ivec2(floor(mod(local, float(stride))));
        int bit = subcell.x + subcell.y * stride;
        vec4 mask = floor(texelFetch(Sampler2, ivec2(pixel.x * 2 + bit / 32, pixel.y), 0) * 255.0 + 0.5);
        int value = int(mask[(bit % 32) / 8]);
        if ((value & (1 << (bit % 8))) == 0) return vec4(0.0, 0.0, 0.0, -1.0);
        return texelFetch(Sampler0, pixel, 0);
    }
    return vec4(0.0, 0.0, 0.0, -1.0);
}

void main() {
    vec2 plane = (screenPos / Viewport - 0.5) * WorldSpan;
    vec4 terrain = terrainAt(plane);
    if (terrain.a >= 0.0) {
        vec3 light = mix(Lighting, max(Lighting, vec3(1.0, 0.9, 0.75)), terrain.a * terrain.a);
        fragColor = vec4(terrain.rgb * light, 1.0); return;
    }
    vec2 world = CloudOrigin + plane;
    vec3 color = vec3(0.012, 0.018, 0.026);
    for (int layer = 0; layer < 3; layer++) {
        float level = float(layer);
        vec2 uv = fract((world + vec2(level * 73.0, level * 131.0)) / (CloudPeriod * exp2(level)));
        float cloud = texture(Sampler1, uv).a;
        color = mix(color, vec3(0.045, 0.065, 0.09) + level * vec3(0.025, 0.03, 0.04), cloud * 0.88);
    }
    fragColor = vec4(color * Lighting, 1.0);
}
