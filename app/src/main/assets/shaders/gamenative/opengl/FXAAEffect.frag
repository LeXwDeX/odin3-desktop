#version 300 es
// Adapted from GameNative FXAAEffect.java at a1459a722e15a3d5df99633a6f7099bd329c1bea.
// Upstream source SHA-256: bf8396e761b1a315ca133e477c8cd501a862d66c4cd5d4028926ee2993f97fa1.
// GameNative/Winlator: GPL-3.0. See THIRD_PARTY_NOTICES.md and third_party_licenses/.
precision highp float;
precision highp int;
layout(location = 0) out vec4 fragmentColor;
#define FXAA_MIN_REDUCE (1.0 / 128.0)
#define FXAA_MUL_REDUCE (1.0 / 8.0)
#define MAX_SPAN 8.0
uniform sampler2D screenTexture;
uniform vec2 resolution;
const vec3 luma = vec3(0.299, 0.587, 0.114);
void upstreamMain() {
    vec2 invResolution = 1.0 / resolution;
    vec3 rgbNW = texture(screenTexture, (gl_FragCoord.xy + vec2(-1.0, -1.0)) * invResolution).rgb;
    vec3 rgbNE = texture(screenTexture, (gl_FragCoord.xy + vec2( 1.0, -1.0)) * invResolution).rgb;
    vec3 rgbSW = texture(screenTexture, (gl_FragCoord.xy + vec2(-1.0,  1.0)) * invResolution).rgb;
    vec3 rgbSE = texture(screenTexture, (gl_FragCoord.xy + vec2( 1.0,  1.0)) * invResolution).rgb;
    vec3 rgbM  = texture(screenTexture,  gl_FragCoord.xy * invResolution).rgb;
    float lumaNW = dot(rgbNW, luma);
    float lumaNE = dot(rgbNE, luma);
    float lumaSW = dot(rgbSW, luma);
    float lumaSE = dot(rgbSE, luma);
    float lumaM  = dot(rgbM,  luma);
    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));
    vec2 dir;
    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
    dir.y =  ((lumaNW + lumaSW) - (lumaNE + lumaSE));
    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * 0.25 * FXAA_MUL_REDUCE, FXAA_MIN_REDUCE);
    float minDirFactor = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = clamp(dir * minDirFactor, vec2(-MAX_SPAN), vec2(MAX_SPAN)) * invResolution;
    vec4 rgbA = 0.5 * (
        texture(screenTexture, gl_FragCoord.xy * invResolution + dir * (1.0 / 3.0 - 0.5)) +
        texture(screenTexture, gl_FragCoord.xy * invResolution + dir * (2.0 / 3.0 - 0.5)));
    vec4 rgbB = rgbA * 0.5 + 0.25 * (
        texture(screenTexture, gl_FragCoord.xy * invResolution + dir * -0.5) +
        texture(screenTexture, gl_FragCoord.xy * invResolution + dir *  0.5));
    float lumaB = dot(rgbB, vec4(luma, 0.0));
    fragmentColor = lumaB < lumaMin || lumaB > lumaMax ? rgbA : rgbB;
}

// Captured screenshots and offscreen passes are always opaque normalized RGB.
void main() {
    upstreamMain();
    fragmentColor = vec4(clamp(fragmentColor.rgb, 0.0, 1.0), 1.0);
}
