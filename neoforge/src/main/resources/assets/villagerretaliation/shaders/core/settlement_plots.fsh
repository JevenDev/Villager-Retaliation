#version 150

uniform sampler2D SceneDepth;
uniform mat4 InverseViewProjection;
uniform vec3 CameraOffset;
uniform float PlotStates[121];

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(SceneDepth, 0));
    float depth = texture(SceneDepth, uv).r;
    if (depth >= 1.0) discard;
    vec4 relative = InverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 ray = relative.xyz / relative.w;
    vec3 position = ray + CameraOffset + normalize(ray) * 0.015;
    ivec2 plot = ivec2(floor(position.xz / 16.0));
    if (any(lessThan(plot, ivec2(0))) || any(greaterThanEqual(plot, ivec2(11)))) discard;
    float state = PlotStates[plot.x + plot.y * 11];
    if (state < 0.5) discard;

    vec3 color = state < 1.5 ? vec3(0.20, 0.60, 0.95) : state < 2.5 ? vec3(0.25, 0.80, 0.40) : vec3(1.0, 0.72, 0.20);
    float alpha = state < 2.5 ? 0.18 : state < 3.5 ? 0.28 : 0.40;
    float stripe = step(0.70, fract((position.x + position.y + position.z) / 3.0));
    vec2 local = mod(position.xz, 16.0);
    float edge = min(min(local.x, 16.0 - local.x), min(local.y, 16.0 - local.y));
    float border = 1.0 - smoothstep(0.08, 0.08 + max(fwidth(edge), 0.03), edge);
    fragColor = vec4(color, alpha + stripe * 0.045 + border * 0.28);
}
