#version 300 es
// Adapted from GameNative ScalingModeEffect.java at a1459a722e15a3d5df99633a6f7099bd329c1bea.
// Upstream source SHA-256: 66c11a5b4331c162cdb19bbb5f5ef8a3ef6ff584626572a28e389329fc0e88d1.
// GameNative/Winlator: GPL-3.0. See THIRD_PARTY_NOTICES.md and third_party_licenses/.
precision highp float;
precision highp int;
layout(location = 0) out vec4 fragmentColor;
uniform sampler2D screenTexture;
uniform vec2 inputResolution;
uniform vec2 outputResolution;
uniform float scaleMode;
in vec2 vUV;
void upstreamMain() {
    vec2 uv;
    if (scaleMode > 1.5) {
        uv = vUV;
    } else {
        float scale = scaleMode > 0.5
            ? max(outputResolution.x / inputResolution.x, outputResolution.y / inputResolution.y)
            : min(outputResolution.x / inputResolution.x, outputResolution.y / inputResolution.y);
        vec2 scaledSize = inputResolution * scale;
        vec2 offset = 0.5 * (outputResolution - scaledSize);
        uv = (gl_FragCoord.xy - offset) / scaledSize;
        if (scaleMode < 0.5) {
            if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                fragmentColor = vec4(0.0, 0.0, 0.0, 1.0);
                return;
            }
        } else {
            uv = clamp(uv, 0.0, 1.0);
        }
    }
    fragmentColor = texture(screenTexture, uv);
}

// Captured screenshots and offscreen passes are always opaque normalized RGB.
void main() {
    upstreamMain();
    fragmentColor = vec4(clamp(fragmentColor.rgb, 0.0, 1.0), 1.0);
}
