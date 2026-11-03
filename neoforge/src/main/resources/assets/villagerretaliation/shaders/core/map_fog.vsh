#version 150

in vec3 Position;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 CanvasOrigin;
uniform vec2 Viewport;
uniform float DiscoveryPass;
out vec2 screenPos;

void main() {
    screenPos = Position.xy - CanvasOrigin;
    gl_Position = DiscoveryPass > 0.5
            ? vec4(screenPos / Viewport * vec2(2.0, -2.0) + vec2(-1.0, 1.0), 0.0, 1.0)
            : ProjMat * ModelViewMat * vec4(Position, 1.0);
}
