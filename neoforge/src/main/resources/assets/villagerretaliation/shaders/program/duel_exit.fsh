#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float UTime;
uniform float WipeProgress;
uniform float EffectOpacity;
uniform float PulseStrength;
uniform float WinPulse;
uniform float LossPulse;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 local = fract(p);
    vec2 blend = local * local * (3.0 - 2.0 * local);
    float a = hash(cell);
    float b = hash(cell + vec2(1.0, 0.0));
    float c = hash(cell + vec2(0.0, 1.0));
    float d = hash(cell + vec2(1.0, 1.0));
    return mix(a, b, blend.x)
        + (c - a) * blend.y * (1.0 - blend.x)
        + (d - b) * blend.x * blend.y;
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 5; octave++) {
        value += amplitude * noise(p);
        p = p * 2.02 + vec2(15.7, 8.3);
        amplitude *= 0.5;
    }
    return value;
}

void main() {
    vec2 uv = texCoord;
    vec3 scene = texture(DiffuseSampler, uv).rgb;
    float progress = clamp(WipeProgress, 0.0, 1.0);
    float opacity = clamp(EffectOpacity, 0.0, 1.0);

    vec2 safeSize = max(OutSize, vec2(1.0));
    vec2 aspectUv = uv;
    aspectUv.x *= safeSize.x / safeSize.y;
    float time = UTime * 0.42;

    vec2 flowA = aspectUv * 4.4 + vec2(time * 0.54, -time * 0.72);
    float firstNoise = fbm(flowA);
    vec2 flowB = aspectUv * 7.2
        + vec2(-time * 0.38, time * 0.48)
        + vec2(firstNoise * 1.6, firstNoise * -1.15);
    float secondNoise = fbm(flowB);

    vec2 edgeDistance = min(uv, 1.0 - uv);
    float nearestEdge = min(edgeDistance.x, edgeDistance.y);
    float liquidFront = progress
        - nearestEdge * 1.58
        + (firstNoise - 0.5) * 0.24
        + (secondNoise - 0.5) * 0.13;
    float wipeEnabled = smoothstep(0.0, 0.035, progress);
    float grayscaleMask = smoothstep(-0.055, 0.055, liquidFront) * opacity * wipeEnabled;
    float smokeFront = 1.0 - smoothstep(0.025, 0.16, abs(liquidFront));
    smokeFront *= opacity * wipeEnabled;

    float luminance = dot(scene, vec3(0.2126, 0.7152, 0.0722));
    vec3 monochrome = vec3(luminance) * 0.94;
    vec3 color = mix(scene, monochrome, grayscaleMask);

    float tendrils = smoothstep(0.48, 0.86, secondNoise);
    vec3 smokeColor = vec3(0.055, 0.06, 0.065);
    float smokeAmount = smokeFront * (0.10 + tendrils * 0.24);
    color = mix(color, smokeColor, smokeAmount);

    float silverEdge = smokeFront * smoothstep(0.56, 0.92, firstNoise) * 0.11;
    color += vec3(0.58, 0.61, 0.64) * silverEdge;

    float pulse = clamp(PulseStrength, 0.0, 1.0);
    float edgeVignette = 1.0 - smoothstep(0.015, 0.34, nearestEdge);
    float pulseTexture = 0.42 + secondNoise * 0.58;
    float pulseMask = pulse * edgeVignette * pulseTexture;
    float pulseMonochrome = pulseMask * (0.055 + clamp(LossPulse, 0.0, 1.0) * 0.055);
    color = mix(color, vec3(dot(color, vec3(0.2126, 0.7152, 0.0722))), pulseMonochrome);

    vec3 neutralTint = vec3(0.55, 0.58, 0.62);
    vec3 winTint = vec3(0.92, 0.72, 0.24);
    vec3 lossTint = vec3(0.34, 0.38, 0.46);
    vec3 pulseTint = mix(neutralTint, winTint, clamp(WinPulse, 0.0, 1.0));
    pulseTint = mix(pulseTint, lossTint, clamp(LossPulse, 0.0, 1.0));
    color = mix(color, color * 0.92 + pulseTint * 0.08, pulseMask * 0.42);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
