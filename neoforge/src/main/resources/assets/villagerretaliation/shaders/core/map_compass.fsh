#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 Rotation;
uniform float Opacity;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    ivec2 size = textureSize(Sampler0, 0);
    ivec2 pixel = ivec2(floor(texCoord0 * vec2(size)));
    vec4 background = texelFetch(Sampler0, pixel, 0);
    // rotate samples around the hub after snapping to the dial's texel grid
    vec2 offset = vec2(pixel) + 0.5 - vec2(size) * 0.5;
    mat2 inverseRotation = mat2(Rotation.x, -Rotation.y, Rotation.y, Rotation.x);
    ivec2 source = ivec2(floor(inverseRotation * offset + vec2(size) * 0.5));
    vec4 needle = vec4(0.0);
    if (all(greaterThanEqual(source, ivec2(0))) && all(lessThan(source, size))) {
        needle = texelFetch(Sampler1, source, 0);
    }
    float alpha = needle.a + background.a * (1.0 - needle.a);
    if (alpha == 0.0) discard;
    vec3 color = (needle.rgb * needle.a + background.rgb * background.a * (1.0 - needle.a)) / alpha;
    fragColor = vec4(color, alpha * Opacity);
}
