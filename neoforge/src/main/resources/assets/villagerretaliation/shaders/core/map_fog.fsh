#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform float DiscoveryPass;
uniform float OpeningProgress;
uniform float ParchmentColors;
uniform vec2 Viewport;
uniform vec2 WorldSpan;
uniform vec4 PageX;
uniform vec4 PageZ;
uniform float FirstLevel;
uniform float TerrainTexelSize;
uniform vec2 CloudOrigin;
uniform float CloudPeriod;
uniform vec3 CloudDriftX;
uniform vec3 CloudDriftZ;
uniform vec3 Lighting;
in vec2 screenPos;
out vec4 fragColor;

const vec3 PARCHMENT_INK = vec3(0.22, 0.21, 0.15);

float paperHash(vec2 point) {
    vec3 value = fract(vec3(point.xyx) * 0.1031);
    value += dot(value, value.yzx + 33.33);
    return fract((value.x + value.y) * value.z);
}

float paperNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 blend = fract(point);
    blend = blend * blend * (3.0 - 2.0 * blend);
    return mix(mix(paperHash(cell), paperHash(cell + vec2(1.0, 0.0)), blend.x),
            mix(paperHash(cell + vec2(0.0, 1.0)), paperHash(cell + vec2(1.0)), blend.x), blend.y);
}

vec3 parchmentColor(vec3 color, vec3 light) {
    float luminance = clamp(dot(color, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
    float tone = smoothstep(0.08, 0.84, luminance);
    float water = smoothstep(0.08, 0.3, color.b - max(color.r, color.g));
    vec3 wash = vec3(0.48, 0.455, 0.35);
    vec3 paper = vec3(0.96, 0.89, 0.72);
    vec3 palette = mix(PARCHMENT_INK, wash, smoothstep(0.0, 0.55, tone));
    palette = mix(palette, paper, smoothstep(0.5, 1.0, tone));
    float waterDepth = smoothstep(0.55, 1.0, color.b);
    vec3 waterColor = mix(vec3(0.69, 0.67, 0.57), vec3(0.93, 0.88, 0.72), waterDepth);
    palette = mix(palette, waterColor, water);
    palette = mix(palette, color, 0.025);
    // keep the paper grain fixed on the page while the terrain pans and zooms
    float grain = paperHash(floor(screenPos));
    float mottling = paperNoise(screenPos / 73.0) * 0.6 + paperNoise(screenPos / 19.0) * 0.4;
    vec2 edge = screenPos / Viewport * 2.0 - 1.0;
    float aging = pow(clamp(dot(edge, edge) * 0.5, 0.0, 1.0), 2.0);
    palette *= 0.985 + (mottling - 0.5) * 0.035 + (grain - 0.5) * 0.009 - aging * 0.055;
    return clamp(palette, 0.0, 1.0) * dot(light, vec3(0.2126, 0.7152, 0.0722));
}

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

float bankNoise(vec2 point, float period) {
    vec2 cell = floor(point);
    vec2 blend = fract(point);
    blend = blend * blend * (3.0 - 2.0 * blend);
    return mix(mix(paperHash(mod(cell, period)), paperHash(mod(cell + vec2(1.0, 0.0), period)), blend.x),
            mix(paperHash(mod(cell + vec2(0.0, 1.0), period)), paperHash(mod(cell + vec2(1.0), period)), blend.x), blend.y);
}

float cloudStep() {
    float pixelSize = max(WorldSpan.x / Viewport.x, WorldSpan.y / Viewport.y);
    return max(TerrainTexelSize, exp2(floor(log2(pixelSize))));
}

vec2 cloudTexel(vec2 plane) {
    return floor((CloudOrigin + plane) / cloudStep());
}

// cc0 references: https://godotshaders.com/shader/clouds-in-motion/
// https://godotshaders.com/shader/pixelated-radial-2d-clouds/
float cloudCrest(float x) {
    return 0.3 * sin(x * 0.1) + 0.3 * sin(x * 0.553) + 0.42 * sin(x * 1.2);
}

float cloudLayer(vec2 point, float wind) {
    point *= 3.5;
    point.x += wind * 0.5;
    float cell = floor(point.x);
    float distance = point.y - cloudCrest(point.x);
    for (int neighbor = -1; neighbor <= 1; neighbor++) {
        float x = cell + float(neighbor);
        vec2 center = vec2(x + 0.5, cloudCrest(x + 0.5));
        float radius = 0.75 + 0.25 * sin(40.0 * x + wind * 0.5);
        distance = min(distance, length(point - center) - radius);
    }
    return distance;
}

vec4 parchmentClouds(vec2 plane, float haze) {
    float pixelSize = max(WorldSpan.x / Viewport.x, WorldSpan.y / Viewport.y);
    float cells = max(4.0, floor(CloudPeriod / clamp(pixelSize * 72.0, 16.0, 512.0) / 4.0) * 4.0);
    float scale = CloudPeriod / cells;
    float stepSize = cloudStep();
    vec2 point = (cloudTexel(plane) + 0.5) * stepSize / scale;
    vec2 wind = vec2(CloudDriftX.x, CloudDriftZ.x) / scale;
    float first = bankNoise(point - wind + vec2(11.37, 7.91), cells);
    vec2 upperWind = vec2(CloudDriftX.y, CloudDriftZ.y) / scale;
    float second = bankNoise(point - upperWind + vec2(31.7, 13.9), cells * 2.0);
    float noise = (first + second) * 0.5;
    float radial = mix(noise, haze, 0.75);
    vec3 shadow = vec3(0.65, 0.61, 0.48);
    vec3 paper = vec3(0.94, 0.88, 0.72);
    vec3 color = shadow;
    for (int layer = 0; layer < 6; layer++) {
        float depth = float(layer) / 5.0;
        float motion = (first - 0.5) * (2.0 + depth * 3.0) + (second - 0.5) * (3.0 - depth * 2.0) + depth * 17.0;
        vec2 position = vec2(point.x + second * 1.4, (0.5 - radial) * 2.0 + 0.3 + 1.5 * (depth - 0.5));
        float edge = cloudLayer(position, motion);
        float cover = 1.0 - smoothstep(0.0, 0.08, edge);
        color = mix(color, mix(shadow, paper, depth), cover);
    }
    color *= 0.985 + (paperHash(floor(screenPos)) - 0.5) * 0.009;
    float front = mix(0.20, 0.035, smoothstep(0.25, 0.75, noise));
    float opacity = smoothstep(front, 0.48, haze);
    return vec4(color * dot(Lighting, vec3(0.2126, 0.7152, 0.0722)), opacity);
}

float cloudDither(vec2 plane) {
    ivec2 pixel = ivec2(cloudTexel(plane));
    int x = pixel.x & 3, y = pixel.y & 3;
    int low = ((x & 1) ^ (y & 1)) * 2 + (y & 1);
    int high = (((x >> 1) & 1) ^ ((y >> 1) & 1)) * 2 + ((y >> 1) & 1);
    return (float(low * 4 + high) + 0.5) / 16.0;
}

vec2 discoveryUv() {
    return vec2(screenPos.x / Viewport.x, 1.0 - screenPos.y / Viewport.y);
}

float cloudHaze(vec2 plane) {
    vec2 position = ((cloudTexel(plane) + 0.5) * cloudStep() - CloudOrigin) / WorldSpan + 0.5;
    return 1.0 - texture(Sampler5, vec2(position.x, 1.0 - position.y)).r;
}

vec4 blurDiscovery() {
    vec2 direction = DiscoveryPass < 2.5 ? vec2(2.0, 0.0) : vec2(0.0, 2.0);
    vec2 stepSize = direction / vec2(textureSize(Sampler5, 0));
    float coverage = 0.0;
    for (int tap = -4; tap <= 4; tap++) {
        int distance = abs(tap);
        float weight = distance == 0 ? 13.0 : distance == 1 ? 10.0 : distance == 2 ? 7.0 : distance == 3 ? 4.0 : 1.0;
        coverage += texture(Sampler5, discoveryUv() + stepSize * float(tap)).r * weight;
    }
    return vec4(vec3(coverage / 57.0), 1.0);
}

float waterRipple(vec2 plane) {
    vec2 world = CloudOrigin + plane;
    float wave = sin(world.y * 0.39269908 + sin(world.x * 0.09817477) * 0.7);
    float pixelSize = max(WorldSpan.x / Viewport.x, WorldSpan.y / Viewport.y);
    return wave * (1.0 - smoothstep(2.0, 6.0, pixelSize)) * 0.012;
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

vec4 openingClouds() {
    float y = screenPos.y / Viewport.y;
    vec3 color = vec3(0.0);
    float remaining = 1.0;
    ivec2 size = textureSize(Sampler1, 0);
    for (int layer = 0; layer < 3; layer++) {
        float depth = float(layer);
        float rise = smoothstep(0.0, 0.33 - depth * 0.035, OpeningProgress);
        float leave = smoothstep(0.53 - depth * 0.065, 1.0 - depth * 0.06, OpeningProgress);
        float top = mix(1.2, -0.65, rise) - leave * 1.8;
        float bottom = top + 1.9;
        vec2 position = (screenPos - vec2(0.0, top * Viewport.y)) / (6.0 + depth * 3.0)
                + vec2(depth * 73.0, depth * 131.0);
        float cloud = silhouette(position, size);
        ivec2 crest = ivec2(floor(position.x / 3.0), int(depth * 17.0));
        float leading = 0.08 + cloudAlpha(crest, size) * 0.10 + cloudAlpha(crest + ivec2(13, 7), size) * 0.06;
        float trailing = 0.08 + cloudAlpha(crest + ivec2(37, 23), size) * 0.10 + cloudAlpha(crest + ivec2(51, 31), size) * 0.06;
        float body = step(top + leading, y) * step(y, bottom - trailing);
        float opacity = step(top, y) * step(y, bottom) * max(body, cloud);
        float shade = 1.0 - depth * 0.05 - (1.0 - cloud) * 0.025;
        color += vec3(shade) * Lighting * opacity * remaining;
        remaining *= 1.0 - opacity;
    }
    float alpha = 1.0 - remaining;
    return vec4(alpha > 0.0 ? color / alpha : vec3(0.0), alpha);
}

void main() {
    if (OpeningProgress >= 0.0) {
        fragColor = openingClouds();
        return;
    }
    if (DiscoveryPass > 1.5) {
        fragColor = blurDiscovery();
        return;
    }
    vec2 plane = (screenPos / Viewport - 0.5) * WorldSpan;
    ivec2 pixel = terrainPixel(plane);
    if (DiscoveryPass > 0.5) {
        fragColor = vec4(vec3(pixel.x >= 0 ? 1.0 : 0.0), 1.0);
        return;
    }
    // only discovered pixels may supply terrain colors to the cloud fringe
    if (pixel.x >= 0) {
        vec4 terrain = texelFetch(Sampler0, pixel, 0);
        vec3 light = mix(Lighting, max(Lighting, vec3(1.0, 0.9, 0.75)), terrain.a * terrain.a);
        vec3 color = terrain.rgb * light;
        if (ParchmentColors > 0.5) {
            color = parchmentColor(terrain.rgb, light);
            float water = smoothstep(0.08, 0.3, terrain.b - max(terrain.r, terrain.g));
            color *= 1.0 + water * waterRipple(plane);
            float haze = cloudHaze(plane);
            if (haze > 0.0) {
                vec4 cloud = parchmentClouds(plane, haze);
                if (cloud.a > cloudDither(plane)) color = cloud.rgb;
            }
        }
        fragColor = vec4(color, 1.0);
        return;
    }
    if (ParchmentColors > 0.5) {
        float haze = cloudHaze(plane);
        fragColor = vec4(parchmentClouds(plane, haze).rgb, 1.0);
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
    color += Lighting * 0.86 * remaining;
    fragColor = vec4(color, 1.0);
}
