#version 150

uniform sampler2D Sampler0;
uniform float ParchmentColors;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    ivec2 size = textureSize(Sampler0, 0);
    ivec2 pixel = clamp(ivec2(floor(texCoord0 * vec2(size))), ivec2(0), size - 1);
    vec4 source = texelFetch(Sampler0, pixel, 0);
    if (source.a == 0.0) discard;
    vec3 color = source.rgb;
    if (ParchmentColors > 0.5) {
        float light = dot(color, vec3(0.2126, 0.7152, 0.0722)) * 0.75
                + max(color.r, max(color.g, color.b)) * 0.25;
        vec3 paper = mix(vec3(0.22, 0.21, 0.15), vec3(0.96, 0.89, 0.72), light);
        color = mix(paper, color, 0.025);
    }
    fragColor = vec4(color, source.a);
}
