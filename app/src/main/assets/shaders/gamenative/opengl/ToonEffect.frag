#version 300 es
// Adapted from GameNative ToonEffect.java at a1459a722e15a3d5df99633a6f7099bd329c1bea.
// Upstream source SHA-256: 770af3831d8063d21956ea9032fd375d24a179c5ac567cea139d8a58c406300f.
// GameNative/Winlator: GPL-3.0. See THIRD_PARTY_NOTICES.md and third_party_licenses/.
precision highp float;
precision highp int;
layout(location = 0) out vec4 fragmentColor;
uniform sampler2D screenTexture;
uniform vec2 resolution;
void upstreamMain() {
    vec2 uv = gl_FragCoord.xy / resolution;
    float edgeThreshold = 0.2;
    vec2 offset = vec2(1.0) / resolution;
    vec3 colorCenter = texture(screenTexture, uv).rgb;
    vec3 colorLeft = texture(screenTexture, uv - vec2(offset.x, 0.0)).rgb;
    vec3 colorRight = texture(screenTexture, uv + vec2(offset.x, 0.0)).rgb;
    vec3 colorUp = texture(screenTexture, uv - vec2(0.0, offset.y)).rgb;
    vec3 colorDown = texture(screenTexture, uv + vec2(0.0, offset.y)).rgb;
    float diffHorizontal = length(colorRight - colorLeft);
    float diffVertical = length(colorUp - colorDown);
    float edgeFactor = step(edgeThreshold, diffHorizontal + diffVertical);
    vec3 outlineColor = mix(colorCenter, vec3(0.0), edgeFactor);
    fragmentColor = vec4(outlineColor, 1.0);
}

// Captured screenshots and offscreen passes are always opaque normalized RGB.
void main() {
    upstreamMain();
    fragmentColor = vec4(clamp(fragmentColor.rgb, 0.0, 1.0), 1.0);
}
