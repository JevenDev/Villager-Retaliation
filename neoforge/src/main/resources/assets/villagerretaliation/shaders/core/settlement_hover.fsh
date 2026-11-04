#version 150

uniform sampler2D SceneDepth;
uniform mat4 InverseViewProjection;
uniform vec3 CameraOffset;
uniform vec3 Cursor;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(SceneDepth, 0));
    float depth = texture(SceneDepth, uv).r;
    vec4 relative = InverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 surface = relative.xyz / relative.w + CameraOffset;
    vec3 normal = normalize(cross(dFdx(surface), dFdy(surface)));
    vec2 footprint = max(fwidth(surface.xz), vec2(0.001));
    if (depth >= 1.0 || abs(normal.y) < 0.65) discard;

    vec2 offset = surface.xz - Cursor.xz;
    float fade = 1.0 - smoothstep(2.0, 9.0, length(offset));
    fade *= 1.0 - smoothstep(0.5, 3.0, abs(surface.y - Cursor.y));
    if (fade <= 0.001) discard;

    vec2 edge = abs(fract(surface.xz + 0.5) - 0.5);
    vec2 gridCoverage = 1.0 - smoothstep(vec2(0.0), footprint, max(edge - 0.012, vec2(0.0)));
    float grid = max(gridCoverage.x, gridCoverage.y);
    vec2 squareOffset = abs(surface.xz - (floor(Cursor.xz) + 0.5));
    float squareDistance = max(squareOffset.x, squareOffset.y);
    float highlight = 1.0 - smoothstep(0.0, max(footprint.x, footprint.y),
            max(abs(squareDistance - 0.5) - 0.025, 0.0));
    float glow = (1.0 - smoothstep(0.5, 0.8, squareDistance)) * 0.035;
    float alpha = (grid * 0.22 + highlight * 0.78 + glow) * fade;
    if (alpha <= 0.002) discard;
    fragColor = vec4(mix(vec3(0.72, 0.84, 0.78), vec3(1.0, 0.98, 0.85), highlight), alpha);
}
