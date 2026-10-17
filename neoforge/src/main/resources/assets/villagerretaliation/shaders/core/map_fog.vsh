#version 150

in vec3 Position;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 CanvasOrigin;
out vec2 screenPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    screenPos = Position.xy - CanvasOrigin;
}
