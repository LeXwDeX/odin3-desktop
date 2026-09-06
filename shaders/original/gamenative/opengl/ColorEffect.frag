#version 300 es
// Adapted from GameNative ColorEffect.java at a1459a722e15a3d5df99633a6f7099bd329c1bea.
// Upstream source SHA-256: e12a43128a0c3843552876c43235999125ead2ab165e60b9ba6100d6af1fbae1.
// GameNative/Winlator: GPL-3.0. See THIRD_PARTY_NOTICES.md and third_party_licenses/.
precision highp float;
precision highp int;
layout(location = 0) out vec4 fragmentColor;
uniform sampler2D screenTexture;
uniform float brightness;
uniform float contrast;
uniform float gamma;
in vec2 vUV;
void upstreamMain() {
    vec4 texelColor = texture(screenTexture, vUV);
    vec3 color = texelColor.rgb;
    color = clamp(color + brightness, 0.0, 1.0);
    color = (color - 0.5) * clamp(contrast + 1.0, 0.5, 2.0) + 0.5;
    color = clamp(color, 0.0, 1.0);
    color = pow(color, vec3(1.0 / gamma));
    fragmentColor = vec4(color, texelColor.a);
}

// Captured screenshots and offscreen passes are always opaque normalized RGB.
void main() {
    upstreamMain();
    fragmentColor = vec4(clamp(fragmentColor.rgb, 0.0, 1.0), 1.0);
}
