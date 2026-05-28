#version 150

const float SOURCE_WIDTH = 1920.0;
const float SOURCE_HEIGHT = 1080.0;

uniform float RectLeft;
uniform float RectTop;
uniform float RectRight;
uniform float RectBottom;
uniform float AccentRed;
uniform float AccentGreen;
uniform float AccentBlue;
uniform float FillProgress;
uniform float Alpha;
uniform float ElapsedTicks;
uniform float ElapsedMillis;
uniform float ExitElapsedMillis;
uniform float ChromeElapsedMillis;
uniform float ChromeExitElapsedMillis;
uniform float ScreenWidth;
uniform float ScreenHeight;
uniform float MouseX;
uniform float MouseY;
uniform float Hovered;
uniform float Mode;

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

float band(float value, float center, float width) {
    return 1.0 - smoothstep(width, width + 0.018, abs(value - center));
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

void composite(vec3 sourceColor, float sourceAlpha, inout vec3 premultipliedColor, inout float alpha) {
    float clampedAlpha = clamp(sourceAlpha, 0.0, 1.0);
    premultipliedColor = sourceColor * clampedAlpha + premultipliedColor * (1.0 - clampedAlpha);
    alpha = clampedAlpha + alpha * (1.0 - clampedAlpha);
}

void compositeCapped(vec3 sourceColor, float sourceAlpha, inout vec3 premultipliedColor, inout float alpha) {
    float clampedAlpha = clamp(sourceAlpha, 0.0, 1.0);
    if (clampedAlpha <= 0.001) {
        return;
    }

    float nextAlpha = max(alpha, clampedAlpha);
    vec3 currentColor = alpha <= 0.001 ? sourceColor : premultipliedColor / max(alpha, 0.001);
    float sourceWeight = clamp(clampedAlpha / max(nextAlpha, 0.001), 0.0, 1.0);
    vec3 nextColor = mix(currentColor, sourceColor, sourceWeight);
    premultipliedColor = nextColor * nextAlpha;
    alpha = nextAlpha;
}

vec4 panelLayerState(
    vec2 size,
    float delayMillis,
    float durationMillis,
    float startXRatio,
    float startYRatio,
    float startScale,
    float exitDelayMillis,
    float exitXRatio,
    float exitFallRatio
) {
    float progress = normalizedProgress(ElapsedMillis, delayMillis, durationMillis);
    if (progress <= 0.0) {
        return vec4(0.0, 0.0, 1.0, 0.0);
    }

    float easedProgress = easeOutBack(progress);
    float settle = easeOutCubic(progress);
    float alpha = clamp(progress * 1.35, 0.0, 1.0);
    vec2 offset = vec2(
        startXRatio * size.x * (1.0 - easedProgress),
        startYRatio * size.y * (1.0 - easedProgress)
    );
    float scale = 1.0 + (startScale - 1.0) * (1.0 - settle);

    float pulse = idlePulse(ElapsedMillis);
    if (pulse > 0.0) {
        offset += vec2(startXRatio * size.x, startYRatio * size.y) * 0.012 * pulse;
    }

    float mouseSettle = easeOutCubic(normalizedProgress(ElapsedMillis, delayMillis + durationMillis, 280.0));
    vec2 screenSize = vec2(max(ScreenWidth, 1.0), max(ScreenHeight, 1.0));
    vec2 mouseRatio = clamp(vec2(MouseX, MouseY) / screenSize, vec2(0.0), vec2(1.0)) * 2.0 - 1.0;
    float layerDepth = max(0.45, abs(startXRatio) + abs(startYRatio));
    offset += vec2(mouseRatio.x * layerDepth * 4.0, mouseRatio.y * layerDepth * 2.4) * mouseSettle;

    if (ExitElapsedMillis >= 0.0) {
        float exitProgress = normalizedProgress(ExitElapsedMillis, exitDelayMillis, 290.0);
        float fall = easeInBack(exitProgress);
        float fade = 1.0 - smoothstep01(normalizedProgress(ExitElapsedMillis, exitDelayMillis + 120.0, 180.0));
        alpha *= fade;
        offset += vec2(exitXRatio * size.x, exitFallRatio * size.y) * fall;
        scale += 0.045 * fall;
    }

    return vec4(offset, scale, clamp(alpha, 0.0, 1.0));
}

vec2 panelSourcePoint(vec2 p, vec2 size, vec4 layer) {
    float scale = max(layer.z, 0.001);
    return (p - layer.xy - size * 0.5) / scale + size * 0.5;
}

vec4 chromeLayerState(
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
    float progress = normalizedProgress(ChromeElapsedMillis, delayMillis, durationMillis);
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

    float pulse = idlePulse(ChromeElapsedMillis);
    if (pulse > 0.0) {
        offset += vec2(startXRatio * screenSize.x, startYRatio * screenSize.y) * 0.012 * pulse;
    }

    float mouseSettle = easeOutCubic(normalizedProgress(ChromeElapsedMillis, delayMillis + durationMillis, 280.0));
    vec2 mouseRatio = clamp(vec2(MouseX, MouseY) / screenSize, vec2(0.0), vec2(1.0)) * 2.0 - 1.0;
    float layerDepth = max(0.45, abs(startXRatio) + abs(startYRatio));
    offset += vec2(mouseRatio.x * layerDepth * 5.0, mouseRatio.y * layerDepth * 3.0) * mouseSettle;

    if (ChromeExitElapsedMillis >= 0.0) {
        float exitProgress = normalizedProgress(ChromeExitElapsedMillis, exitDelayMillis, 290.0);
        float fall = easeInBack(exitProgress);
        float fade = 1.0 - smoothstep01(normalizedProgress(ChromeExitElapsedMillis, exitDelayMillis + 120.0, 180.0));
        alpha *= fade;
        offset += vec2(exitXRatio * screenSize.x, exitFallRatio * screenSize.y) * fall;
        scale += 0.045 * fall;
    }

    return vec4(offset, scale, clamp(alpha, 0.0, 1.0));
}

vec2 chromeSourcePoint(vec2 screenPoint, vec4 layer) {
    float scale = max(layer.z, 0.001);
    vec2 screenSize = vec2(max(ScreenWidth, 1.0), max(ScreenHeight, 1.0));
    vec2 centered = (screenPoint - layer.xy - screenSize * 0.5) / scale + screenSize * 0.5;
    return vec2(
        centered.x * SOURCE_WIDTH / screenSize.x,
        centered.y * SOURCE_HEIGHT / screenSize.y
    );
}

bool insideMainTransparentChrome(vec2 screenPoint) {
    vec4 lowerVeilLayer = chromeLayerState(0.0, 260.0, -0.04, 0.16, 1.025, 80.0, -0.10, 1.02);
    vec2 lowerVeilPoint = chromeSourcePoint(screenPoint, lowerVeilLayer);

    return lowerVeilLayer.w > 0.001 && insideQuad(
        lowerVeilPoint,
        vec2(-560.0, 867.0),
        vec2(2480.0, 590.0),
        vec2(2480.0, 1360.0),
        vec2(-560.0, 1360.0)
    );
}

vec4 panelColor(vec2 p, vec2 size, vec2 uv) {
    vec3 premultipliedColor = vec3(0.0);
    float alpha = 0.0;

    vec4 lowerVeilLayer = panelLayerState(size, 0.0, 260.0, 0.16, -0.04, 1.025, 80.0, 1.02, 0.10);
    vec4 lowerShadowLayer = panelLayerState(size, 90.0, 240.0, 0.30, -0.06, 1.04, 0.0, 1.12, 0.16);
    vec4 rightShadowLayer = panelLayerState(size, 210.0, 250.0, -0.05, -0.34, 1.03, 170.0, 1.18, 0.18);
    vec4 rightHighlightLayer = panelLayerState(size, 340.0, 180.0, 0.02, -0.22, 0.98, 260.0, 1.28, 0.30);

    vec2 lowerVeilPoint = panelSourcePoint(p, size, lowerVeilLayer);
    vec2 lowerShadowPoint = panelSourcePoint(p, size, lowerShadowLayer);
    vec2 rightShadowPoint = panelSourcePoint(p, size, rightShadowLayer);
    vec2 rightHighlightPoint = panelSourcePoint(p, size, rightHighlightLayer);

    float lowerVeil = insideQuad(
        lowerVeilPoint,
        vec2(size.x * 0.26, size.y * 1.58),
        vec2(size.x * 0.02, -size.y * 0.34),
        vec2(size.x * 1.34, -size.y * 0.34),
        vec2(size.x * 1.34, size.y * 1.58)
    ) ? 1.0 : 0.0;

    float lowerShadow = insideQuad(
        lowerShadowPoint,
        vec2(size.x * 0.50, size.y * 1.82),
        vec2(size.x * 0.12, -size.y * 0.44),
        vec2(size.x * 1.46, -size.y * 0.44),
        vec2(size.x * 1.46, size.y * 1.82)
    ) ? 1.0 : 0.0;

    float rightShadow = insideTriangle(
        rightShadowPoint,
        vec2(-size.x * 0.34, -size.y * 0.34),
        vec2(size.x * 1.34, -size.y * 0.34),
        vec2(size.x * 1.34, size.y * 0.86)
    ) ? 1.0 : 0.0;

    float rightHighlight = insideTriangle(
        rightHighlightPoint,
        vec2(-size.x * 0.38, -size.y * 0.34),
        vec2(size.x * 1.34, -size.y * 0.34),
        vec2(size.x * 1.34, size.y * 0.46)
    ) ? 1.0 : 0.0;

    float lowerVeilAlpha = lowerVeil * 0.3882353 * lowerVeilLayer.w;
    if (insideMainTransparentChrome(vertexScreenPos)) {
        lowerVeilAlpha = 0.0;
    }

    compositeCapped(vec3(0.0), lowerVeilAlpha, premultipliedColor, alpha);
    composite(vec3(0.0), lowerShadow * lowerShadowLayer.w, premultipliedColor, alpha);
    composite(vec3(0.0627451), rightShadow * rightShadowLayer.w, premultipliedColor, alpha);
    composite(vec3(0.1960784), rightHighlight * rightHighlightLayer.w, premultipliedColor, alpha);
    if (alpha <= 0.001) {
        discard;
    }
    return vec4(premultipliedColor / max(alpha, 0.001), alpha * clamp(Alpha, 0.0, 1.0));
}

vec4 barColor(vec2 p, vec2 size, vec2 uv) {
    float slant = max(2.0, min(size.y * 0.75, 5.0));
    if (!insideQuad(p, vec2(slant, 0.0), vec2(size.x, 0.0), vec2(size.x - slant, size.y), vec2(0.0, size.y))) {
        discard;
    }

    vec3 rawAccent = vec3(AccentRed, AccentGreen, AccentBlue);
    float accentLuma = dot(rawAccent, vec3(0.299, 0.587, 0.114));
    vec3 accent = clamp(mix(vec3(accentLuma), rawAccent, 2.35), vec3(0.0), vec3(1.0));
    float fill = clamp(FillProgress, 0.0, 1.0);
    float filled = 1.0 - smoothstep(fill, fill + 0.012, uv.x);
    float fillEdge = 1.0 - smoothstep(0.0, 0.012, abs(uv.x - fill));
    float trackLine = 1.0 - smoothstep(0.0, 0.018, abs(uv.x - 0.995));
    float topEdge = 1.0 - smoothstep(0.0, 1.0, p.y);
    float bottomEdge = 1.0 - smoothstep(0.0, 1.0, size.y - p.y);

    vec3 premultipliedColor = vec3(0.0);
    float alpha = 0.0;
    composite(vec3(0.012, 0.012, 0.012), 0.92 + Hovered * 0.04, premultipliedColor, alpha);
    composite(vec3(0.36, 0.36, 0.34), topEdge * 0.16, premultipliedColor, alpha);
    composite(vec3(0.0), bottomEdge * 0.30, premultipliedColor, alpha);
    composite(accent, filled * (0.96 + Hovered * 0.04), premultipliedColor, alpha);
    composite(vec3(1.0), fillEdge * 0.30, premultipliedColor, alpha);
    composite(vec3(0.0), trackLine * 0.22, premultipliedColor, alpha);
    return vec4(premultipliedColor / max(alpha, 0.001), alpha * clamp(Alpha, 0.0, 1.0));
}

void main() {
    vec2 size = max(vec2(RectRight - RectLeft, RectBottom - RectTop), vec2(1.0));
    vec2 p = vertexScreenPos - vec2(RectLeft, RectTop);
    vec2 uv = clamp(p / size, vec2(0.0), vec2(1.0));
    fragColor = Mode < 0.5 ? panelColor(p, size, uv) : barColor(p, size, uv);
}
