#version 300 es
// Adapted from GameNative NTSCCombinedEffect.java at a1459a722e15a3d5df99633a6f7099bd329c1bea.
// Upstream source SHA-256: c89c83a6e59436d022610b9459871a32f2c48b7e278d15eb6fa5d92d30cbeb0c.
// GameNative/Winlator: GPL-3.0. See THIRD_PARTY_NOTICES.md and third_party_licenses/.
precision highp float;
precision highp int;
layout(location = 0) out vec4 fragmentColor;
#define PI 3.14159265
#define SCANLINE_INTENSITY 0.35
#define CHROMA_OFFSET 0.005
#define BLUR_RADIUS 0.002
#define WARP_AMOUNT 0.01
#define SCANLINE_DARKEN 0.5
uniform sampler2D screenTexture;
uniform int FrameCount;
uniform vec2 TextureSize;
uniform vec2 resolution;
in vec2 vUV;
const mat3 yiq_mat = mat3(
   0.299, 0.587, 0.114,
   0.596, -0.275, -0.321,
   0.212, -0.523, 0.311
);
const mat3 yiq2rgb_mat = mat3(
   1.0, 0.956, 0.621,
   1.0, -0.272, -0.647,
   1.0, -1.106, 1.705
);
vec3 applyNTSC(vec2 uv) {
   vec3 col = texture(screenTexture, uv).rgb;
   vec3 yiq = col * yiq_mat;
   float chromaPhase = PI * (mod(uv.y * TextureSize.y, 2.0) + float(FrameCount));
   yiq.y *= cos(chromaPhase * 0.5);
   yiq.z *= sin(chromaPhase * 0.5);
   vec3 rgb = yiq * yiq2rgb_mat;
   vec3 finalColor;
   finalColor.r = texture(screenTexture, uv + vec2(CHROMA_OFFSET, 0.0)).r;
   finalColor.g = texture(screenTexture, uv + vec2(0.0, BLUR_RADIUS)).g;
   finalColor.b = texture(screenTexture, uv - vec2(CHROMA_OFFSET, 0.0)).b;
   return mix(rgb, finalColor, 0.6);
}
vec3 applyScanlines(vec2 uv) {
   vec3 col = texture(screenTexture, uv).rgb;
   float scanline = abs(sin(uv.y * resolution.y * 2.0)) * SCANLINE_INTENSITY;
   col *= 1.0 - (scanline * SCANLINE_DARKEN);
   return col;
}
vec2 applyWarp(vec2 uv) {
   uv = uv * 2.0 - 1.0;
   float r = sqrt(uv.x * uv.x + uv.y * uv.y);
   uv += uv * (r * r) * WARP_AMOUNT;
   return uv * 0.5 + 0.5;
}
void upstreamMain() {
   vec2 warpedUV = applyWarp(vUV);
   vec3 ntscColor = applyNTSC(warpedUV);
   vec3 scanlineColor = applyScanlines(warpedUV);
   vec3 finalColor = mix(ntscColor, scanlineColor, 0.7);
   fragmentColor = vec4(finalColor, 1.0);
}

// Captured screenshots and offscreen passes are always opaque normalized RGB.
void main() {
    upstreamMain();
    fragmentColor = vec4(clamp(fragmentColor.rgb, 0.0, 1.0), 1.0);
}
