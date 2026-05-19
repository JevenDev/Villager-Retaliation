#version 150

uniform float VeilTop;
uniform float FadeHeight;
uniform float CellSize;
uniform float ScreenWidth;
uniform float ArcDepth;

in vec2 vertexScreenPos;

out vec4 fragColor;

float bayer4(ivec2 coord) {
    int x = coord.x & 3;
    int y = coord.y & 3;
    int index = x + y * 4;

    const float thresholds[16] = float[](
         0.0,  8.0,  2.0, 10.0,
        12.0,  4.0, 14.0,  6.0,
         3.0, 11.0,  1.0,  9.0,
        15.0,  7.0, 13.0,  5.0
    );

    return (thresholds[index] + 0.5) / 16.0;
}

void main() {
    float normalizedX = clamp(vertexScreenPos.x / max(ScreenWidth, 1.0), 0.0, 1.0);
    float edgeDistance = abs(normalizedX * 2.0 - 1.0);
    float arcedVeilTop = VeilTop + ArcDepth * edgeDistance * edgeDistance;
    float fadeProgress = clamp((vertexScreenPos.y - arcedVeilTop) / max(FadeHeight, 1.0), 0.0, 1.0);
    if (fadeProgress <= 0.0) {
        discard;
    }

    float quantizedCellSize = max(CellSize, 1.0);
    vec2 cellCoord = floor(vertexScreenPos.xy / quantizedCellSize);
    float threshold = bayer4(ivec2(cellCoord));
    threshold = threshold * 0.99 + 0.005;
    if (fadeProgress < threshold) {
        discard;
    }

    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
}
