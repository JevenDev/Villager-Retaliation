#version 150

uniform float RectLeft;
uniform float RectTop;
uniform float RectRight;
uniform float RectBottom;
uniform float AccentRed;
uniform float AccentGreen;
uniform float AccentBlue;
uniform float Alpha;
uniform float ElapsedTicks;
uniform float Direction;
uniform float Slant;

in vec2 vertexScreenPos;

out vec4 fragColor;

float edgeSign(vec2 a, vec2 b, vec2 p) {
    return (p.x - a.x) * (b.y - a.y) - (p.y - a.y) * (b.x - a.x);
}

bool insideTriangle(vec2 p, vec2 a, vec2 b, vec2 c) {
    float ab = edgeSign(a, b, p);
    float bc = edgeSign(b, c, p);
    float ca = edgeSign(c, a, p);
    return (ab >= 0.0 && bc >= 0.0 && ca >= 0.0)
        || (ab <= 0.0 && bc <= 0.0 && ca <= 0.0);
}

bool insideQuad(vec2 p, vec2 a, vec2 b, vec2 c, vec2 d) {
    return insideTriangle(p, a, b, c) || insideTriangle(p, a, c, d);
}

float lineBand(float value, float center, float halfWidth) {
    return 1.0 - smoothstep(halfWidth, halfWidth + 0.18, abs(value - center));
}

void composite(vec3 sourceColor, float sourceAlpha, inout vec3 premultipliedColor, inout float alpha) {
    float clampedAlpha = clamp(sourceAlpha, 0.0, 1.0);
    premultipliedColor = sourceColor * clampedAlpha + premultipliedColor * (1.0 - clampedAlpha);
    alpha = clampedAlpha + alpha * (1.0 - clampedAlpha);
}

void main() {
    vec2 size = max(vec2(RectRight - RectLeft, RectBottom - RectTop), vec2(1.0));
    vec2 p = vertexScreenPos - vec2(RectLeft, RectTop);
    vec2 uv = p / size;
    float dir = Direction < 0.0 ? -1.0 : 1.0;
    float slant = max(0.0, Slant);

    vec2 a = vec2(dir > 0.0 ? slant : 0.0, 0.0);
    vec2 b = vec2(size.x - (dir > 0.0 ? 0.0 : slant), 0.0);
    vec2 c = vec2(size.x - (dir > 0.0 ? slant : 0.0), size.y);
    vec2 d = vec2(dir > 0.0 ? 0.0 : slant, size.y);
    if (!insideQuad(p, a, b, c, d)) {
        discard;
    }

    vec3 accent = vec3(AccentRed, AccentGreen, AccentBlue);
    float sweep = fract(ElapsedTicks * 0.018);
    float diagonal = uv.x * dir + uv.y * 0.42;
    float shine = lineBand(fract(diagonal - sweep + 1.0), 0.12, 0.055);
    float lowerSlash = smoothstep(0.45, 1.0, uv.x) * (1.0 - smoothstep(0.0, 0.22, abs(uv.y - (0.74 - uv.x * 0.18))));
    float topEdge = 1.0 - smoothstep(0.0, 1.0, p.y);
    float bottomEdge = 1.0 - smoothstep(0.0, 1.0, size.y - p.y);
    float accentBar = 1.0 - smoothstep(0.0, 7.0, dir > 0.0 ? p.x : size.x - p.x);

    vec3 color = vec3(0.02, 0.02, 0.025);
    float alpha = 0.78;
    vec3 premultipliedColor = vec3(0.0);
    float composedAlpha = 0.0;
    composite(color, alpha, premultipliedColor, composedAlpha);
    composite(accent, accentBar * 0.82, premultipliedColor, composedAlpha);
    composite(accent, (topEdge + bottomEdge) * 0.18, premultipliedColor, composedAlpha);
    composite(accent, lowerSlash * 0.18, premultipliedColor, composedAlpha);
    composite(vec3(1.0), shine * 0.025, premultipliedColor, composedAlpha);

    fragColor = vec4(premultipliedColor / max(composedAlpha, 0.001), composedAlpha * clamp(Alpha, 0.0, 1.0));
}
