#version 150

const float SOURCE_WIDTH = 1920.0;
const float SOURCE_HEIGHT = 1080.0;

uniform float ScreenWidth;
uniform float ScreenHeight;
uniform float MouseX;
uniform float MouseY;
uniform float ElapsedMillis;
uniform float ExitElapsedMillis;
uniform float DarkRed;
uniform float DarkGreen;
uniform float DarkBlue;
uniform float LightRed;
uniform float LightGreen;
uniform float LightBlue;

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

float normalizedProgress(float elapsedMillis, float delayMillis, float durationMillis) {
    return clamp((elapsedMillis - delayMillis) / max(durationMillis, 1.0), 0.0, 1.0);
}

float easeOutCubic(float progress) {
    float inverse = 1.0 - progress;
    return 1.0 - inverse * inverse * inverse;
}

float easeOutBack(float progress) {
    float inverseProgress = progress - 1.0;
    float overshoot = 1.45;
    return 1.0 + inverseProgress * inverseProgress * ((overshoot + 1.0) * inverseProgress + overshoot);
}

float easeInBack(float progress) {
    float overshoot = 1.25;
    return progress * progress * ((overshoot + 1.0) * progress - overshoot);
}

float smoothstep01(float progress) {
    return progress * progress * (3.0 - 2.0 * progress);
}

float idlePulse(float elapsedMillis) {
    if (elapsedMillis < 900.0) {
        return 0.0;
    }
    return sin((elapsedMillis - 900.0) * 0.003) * 0.5 + 0.5;
}

vec4 layerState(
    float delayMillis,
    float durationMillis,
    float startXRatio,
    float startYRatio,
    float startScale,
    float exitDelayMillis,
    float exitXRatio,
    float exitFallRatio
) {
    vec2 screenSize = vec2(max(ScreenWidth, 1.0), max(ScreenHeight, 1.0));
    float progress = normalizedProgress(ElapsedMillis, delayMillis, durationMillis);
    if (progress <= 0.0) {
        return vec4(0.0, 0.0, 1.0, 0.0);
    }

    float easedProgress = easeOutBack(progress);
    float settle = easeOutCubic(progress);
    float alpha = clamp(progress * 1.35, 0.0, 1.0);
    vec2 offset = vec2(
        startXRatio * screenSize.x * (1.0 - easedProgress),
        startYRatio * screenSize.y * (1.0 - easedProgress)
    );
    float scale = 1.0 + (startScale - 1.0) * (1.0 - settle);

    float pulse = idlePulse(ElapsedMillis);
    if (pulse > 0.0) {
        offset += vec2(startXRatio * screenSize.x, startYRatio * screenSize.y) * 0.012 * pulse;
    }

    float mouseSettle = easeOutCubic(normalizedProgress(ElapsedMillis, delayMillis + durationMillis, 280.0));
    vec2 mouseRatio = clamp(vec2(MouseX, MouseY) / screenSize, vec2(0.0), vec2(1.0)) * 2.0 - 1.0;
    float layerDepth = max(0.45, abs(startXRatio) + abs(startYRatio));
    offset += vec2(mouseRatio.x * layerDepth * 5.0, mouseRatio.y * layerDepth * 3.0) * mouseSettle;

    if (ExitElapsedMillis >= 0.0) {
        float exitProgress = normalizedProgress(ExitElapsedMillis, exitDelayMillis, 290.0);
        float fall = easeInBack(exitProgress);
        float fade = 1.0 - smoothstep01(normalizedProgress(ExitElapsedMillis, exitDelayMillis + 120.0, 180.0));
        alpha *= fade;
        offset += vec2(exitXRatio * screenSize.x, exitFallRatio * screenSize.y) * fall;
        scale += 0.045 * fall;
    }

    return vec4(offset, scale, clamp(alpha, 0.0, 1.0));
}

vec2 sourcePoint(vec2 screenPoint, vec4 layer) {
    float scale = max(layer.z, 0.001);
    vec2 screenSize = vec2(max(ScreenWidth, 1.0), max(ScreenHeight, 1.0));
    vec2 centered = (screenPoint - layer.xy - screenSize * 0.5) / scale + screenSize * 0.5;
    return vec2(
        centered.x * SOURCE_WIDTH / screenSize.x,
        centered.y * SOURCE_HEIGHT / screenSize.y
    );
}

void composite(vec3 sourceColor, float sourceAlpha, inout vec3 premultipliedColor, inout float alpha) {
    float clampedAlpha = clamp(sourceAlpha, 0.0, 1.0);
    premultipliedColor = sourceColor * clampedAlpha + premultipliedColor * (1.0 - clampedAlpha);
    alpha = clampedAlpha + alpha * (1.0 - clampedAlpha);
}

void renderLowerVeil(vec4 layer, inout vec3 premultipliedColor, inout float alpha) {
    if (layer.w <= 0.001) {
        return;
    }

    vec2 p = sourcePoint(vertexScreenPos, layer);
    bool inside = insideQuad(
        p,
        vec2(-560.0, 867.0),
        vec2(2480.0, 590.0),
        vec2(2480.0, 1360.0),
        vec2(-560.0, 1360.0)
    );
    if (inside) {
        composite(vec3(0.0), 0.3882353 * layer.w, premultipliedColor, alpha);
    }
}

void renderLowerShadow(vec4 layer, inout vec3 premultipliedColor, inout float alpha) {
    if (layer.w <= 0.001) {
        return;
    }

    vec2 p = sourcePoint(vertexScreenPos, layer);
    bool inside = insideQuad(
        p,
        vec2(-560.0, 1053.0),
        vec2(2480.0, 547.0),
        vec2(2480.0, 1360.0),
        vec2(-560.0, 1360.0)
    );
    if (inside) {
        composite(vec3(0.0), layer.w, premultipliedColor, alpha);
    }
}

void renderRightShadow(vec4 layer, inout vec3 premultipliedColor, inout float alpha) {
    if (layer.w <= 0.001) {
        return;
    }

    vec2 p = sourcePoint(vertexScreenPos, layer);
    bool inside = insideTriangle(
        p,
        vec2(2280.0, 183.0),
        vec2(2280.0, 1360.0),
        vec2(682.0, 1360.0)
    );
    if (inside) {
        composite(vec3(DarkRed, DarkGreen, DarkBlue), layer.w, premultipliedColor, alpha);
    }
}

void renderRightHighlight(vec4 layer, inout vec3 premultipliedColor, inout float alpha) {
    if (layer.w <= 0.001) {
        return;
    }

    vec2 p = sourcePoint(vertexScreenPos, layer);
    bool inside = insideTriangle(
        p,
        vec2(2280.0, 138.0),
        vec2(2280.0, 1360.0),
        vec2(1473.0, 1360.0)
    );
    if (inside) {
        composite(vec3(LightRed, LightGreen, LightBlue), layer.w, premultipliedColor, alpha);
    }
}

void main() {
    vec3 premultipliedColor = vec3(0.0);
    float alpha = 0.0;

    renderLowerVeil(layerState(0.0, 260.0, -0.04, 0.16, 1.025, 80.0, -0.10, 1.02), premultipliedColor, alpha);
    renderLowerShadow(layerState(90.0, 240.0, -0.06, 0.30, 1.04, 0.0, -0.16, 1.12), premultipliedColor, alpha);
    renderRightShadow(layerState(210.0, 250.0, 0.34, -0.05, 1.03, 170.0, 0.18, 1.18), premultipliedColor, alpha);
    renderRightHighlight(layerState(340.0, 180.0, 0.22, 0.02, 0.98, 260.0, 0.30, 1.28), premultipliedColor, alpha);

    if (alpha <= 0.001) {
        discard;
    }

    fragColor = vec4(premultipliedColor / alpha, alpha);
}
