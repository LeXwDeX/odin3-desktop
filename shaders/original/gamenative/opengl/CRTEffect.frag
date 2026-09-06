#version 300 es
// Adapted from GameNative CRTEffect.java at a1459a722e15a3d5df99633a6f7099bd329c1bea.
// Upstream source SHA-256: eb6df61fd246b2017facb6475846167d7de7be8b4fe0e4ac30f842dfbd3ac47a.
// GameNative/Winlator: GPL-3.0. See THIRD_PARTY_NOTICES.md and third_party_licenses/.
precision highp float;
precision highp int;
layout(location = 0) out vec4 fragmentColor;
#define CA_AMOUNT 1.0025
#define SCANLINE_INTENSITY_X 0.125
#define SCANLINE_INTENSITY_Y 0.375
// The reference is the Odin 3 1920 x 1080 screen; keep the original CRT cadence there.
uniform vec2 resolution;
uniform sampler2D screenTexture;
in vec2 vUV;
void upstreamMain() {
    vec4 finalColor = texture(screenTexture, vUV);
    finalColor.rgb = vec3(
        texture(screenTexture, (vUV - 0.5) * CA_AMOUNT + 0.5).r,
        finalColor.g,
        texture(screenTexture, (vUV - 0.5) / CA_AMOUNT + 0.5).b
    );
    float scanlineX = abs(sin(vUV.x * resolution.x * (1024.0 / 1920.0)) * 0.5 * SCANLINE_INTENSITY_X);
    float scanlineY = abs(sin(vUV.y * resolution.y * (1024.0 / 1080.0)) * 0.5 * SCANLINE_INTENSITY_Y);
    fragmentColor = vec4(mix(finalColor.rgb, vec3(0.0), scanlineX + scanlineY), finalColor.a);
}

// Captured screenshots and offscreen passes are always opaque normalized RGB.
void main() {
    upstreamMain();
    fragmentColor = vec4(clamp(fragmentColor.rgb, 0.0, 1.0), 1.0);
}
