#version 150

uniform sampler2D SceneDepth;
uniform mat4 InverseViewProjection;
uniform vec3 CameraOffset;
uniform float PlotStates[121];
uniform int OutlineOnly;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(SceneDepth, 0));
    float depth = texture(SceneDepth, uv).r;
    if (depth >= 1.0) discard;
    vec4 relative = InverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 ray = relative.xyz / relative.w;
    vec3 surface = ray + CameraOffset;
    vec3 position = surface + normalize(ray) * 0.015;
    ivec2 plot = ivec2(floor(position.xz / 16.0));
    if (any(lessThan(plot, ivec2(0))) || any(greaterThanEqual(plot, ivec2(11)))) discard;
    float state = PlotStates[plot.x + plot.y * 11];
    if (state < 0.5) discard;

    vec3 color = state < 1.5 ? vec3(0.20, 0.60, 0.95) : state < 2.5 ? vec3(0.25, 0.80, 0.40) : vec3(1.0, 0.72, 0.20);
    float alpha = state < 2.5 ? 0.18 : state < 3.5 ? 0.28 : 0.40;
    vec3 texel = floor(surface * 16.0);
    // a four-block repeat stays anchored when the plot window moves
    float stripe = step(45.0, mod(texel.x + texel.y + texel.z, 64.0));
    vec2 local = mod(texel.xz + vec2(0.5), 256.0);
    vec2 edge = min(local, vec2(256.0) - local);
    vec2 footprint = max(fwidth(surface.xz * 16.0), vec2(1.0));
    // filter a one-texel band on each side of the plot edge
    vec2 coverage = clamp((vec2(1.0) + footprint * 0.5 - edge) / footprint,
            vec2(0.0), min(vec2(1.0), vec2(2.0) / footprint));
    float border = max(coverage.x, coverage.y);
    if (OutlineOnly != 0) {
        if (border <= 0.01) discard;
        fragColor = vec4(color, border * 0.85);
    } else {
        fragColor = vec4(color, alpha + stripe * 0.045 + border * 0.28);
    }
}
