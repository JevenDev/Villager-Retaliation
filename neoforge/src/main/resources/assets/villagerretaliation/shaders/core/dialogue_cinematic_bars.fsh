#version 150

uniform float RectLeft;
uniform float RectTop;
uniform float RectRight;
uniform float RectBottom;
uniform float BarHeight;
uniform float Slant;
uniform float Progress;
uniform float Alpha;

in vec2 vertexScreenPos;

out vec4 fragColor;

void main() {
    vec2 p = vertexScreenPos - vec2(RectLeft, RectTop);
    vec2 size = max(vec2(RectRight - RectLeft, RectBottom - RectTop), vec2(1.0));
    float progress = clamp(Progress, 0.0, 1.0);
    float barHeight = clamp(BarHeight, 0.0, size.y * 0.5);
    float slant = clamp(Slant, -64.0, 64.0);
    float travel = barHeight + abs(slant) + 4.0;
    float topOffset = -travel * (1.0 - progress);
    float bottomOffset = travel * (1.0 - progress);
    float diagonal = slant * clamp(p.x / size.x, 0.0, 1.0);

    float topEdge = floor(topOffset + barHeight + diagonal + 0.5);
    float bottomEdge = floor(size.y - barHeight + bottomOffset + diagonal + 0.5);
    float topMask = p.y <= topEdge ? 1.0 : 0.0;
    float bottomMask = p.y >= bottomEdge ? 1.0 : 0.0;
    float coverage = max(topMask, bottomMask);

    if (coverage <= 0.001) {
        discard;
    }

    fragColor = vec4(vec3(0.0), coverage * clamp(Alpha, 0.0, 1.0));
}
