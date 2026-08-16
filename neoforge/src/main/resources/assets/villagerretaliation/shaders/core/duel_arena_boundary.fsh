#version 150

uniform float GameTime;
uniform float DangerProgress;

in vec4 vertexColor;
in vec3 ringPosition;

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
    for (int octave = 0; octave < 4; octave++) {
        value += amplitude * noise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        amplitude *= 0.5;
    }
    return value;
}

void main() {
    vec2 ringDirection = normalize(ringPosition.xz);
    float time = GameTime * 0.035;
    float danger = clamp(DangerProgress, 0.0, 1.0);
    vec2 flow = ringDirection * 4.6
        + vec2(ringPosition.y * 0.54 - time * (0.48 + danger * 0.34),
               ringPosition.y * -0.31 + time * 0.63);
    float broadNoise = fbm(flow);
    float detailNoise = fbm(flow * vec2(2.15, 1.62) + ringDirection.yx * 2.3
        + vec2(-time * 0.6, time * 0.24));
    float wisps = smoothstep(0.43, 0.82, broadNoise * 0.72 + detailNoise * 0.42);
    float breathing = 0.72 + 0.28 * sin(GameTime * (0.075 + danger * 0.07) + broadNoise * 8.0);

    float lowerFade = smoothstep(-0.45, 0.18, ringPosition.y);
    float upperFade = 1.0 - smoothstep(3.35, 5.25, ringPosition.y);
    float heightFade = lowerFade * upperFade;
    float baseCore = exp(-abs(ringPosition.y - 0.035) * 18.0);
    float baseGlow = exp(-abs(ringPosition.y - 0.08) * 3.4);
    float alpha = vertexColor.a
        * (heightFade * (0.10 + wisps * 0.78 * breathing)
            + baseGlow * 0.52
            + baseCore * 1.08);
    alpha *= 1.0 + danger * 0.38;

    if (alpha < 0.012) {
        discard;
    }

    vec3 amber = vec3(0.72, 0.49, 0.16);
    vec3 pearl = vec3(1.0, 0.93, 0.68);
    vec3 warning = vec3(1.0, 0.18, 0.055);
    vec3 color = mix(amber, pearl, clamp(wisps * 0.72 + baseGlow * 0.32, 0.0, 1.0));
    color = mix(color, warning, danger * (0.34 + wisps * 0.24));

    vec3 baseLight = mix(vec3(1.0, 0.88, 0.42), vec3(1.0, 0.26, 0.07), danger * 0.78);
    float baseLightStrength = clamp(baseGlow * 0.34 + baseCore * 0.82, 0.0, 0.92);
    color = mix(color, baseLight, baseLightStrength);
    fragColor = vec4(color * vertexColor.rgb, clamp(alpha, 0.0, 0.86));
}
