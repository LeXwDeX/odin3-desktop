#version 300 es
precision highp float;
uniform sampler2D screenTexture;
in vec2 vUV;
layout(location = 0) out vec4 fragmentColor;
void main() {
    fragmentColor = vec4(clamp(texture(screenTexture, vUV).rgb, 0.0, 1.0), 1.0);
}
