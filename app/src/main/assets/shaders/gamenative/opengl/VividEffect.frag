#version 300 es
// Adapted from GameNative VividEffect.java at a1459a722e15a3d5df99633a6f7099bd329c1bea.
// Upstream source SHA-256: e24497885a359a936552e44348746b156515680c3e11f9c905bf8e48f49b24ee.
// GameNative/Winlator: GPL-3.0. See THIRD_PARTY_NOTICES.md and third_party_licenses/.
precision highp float;
precision highp int;
layout(location = 0) out vec4 fragmentColor;
uniform sampler2D screenTexture;
uniform vec2 resolution;
const float VIVID_POWER = 1.30;
const float RADIUS_1 = 0.793;
const float RADIUS_2 = 0.870;
void upstreamMain() {
    vec2 texcoord = gl_FragCoord.xy / resolution;
    vec2 px = 1.0 / resolution;
    vec3 color = texture(screenTexture, texcoord).rgb;

    vec3 bloom1 = texture(screenTexture, texcoord + vec2( 1.5, -1.5) * RADIUS_1 * px).rgb;
    bloom1 += texture(screenTexture, texcoord + vec2(-1.5, -1.5) * RADIUS_1 * px).rgb;
    bloom1 += texture(screenTexture, texcoord + vec2( 1.5,  1.5) * RADIUS_1 * px).rgb;
    bloom1 += texture(screenTexture, texcoord + vec2(-1.5,  1.5) * RADIUS_1 * px).rgb;
    bloom1 += texture(screenTexture, texcoord + vec2( 0.0, -2.5) * RADIUS_1 * px).rgb;
    bloom1 += texture(screenTexture, texcoord + vec2( 0.0,  2.5) * RADIUS_1 * px).rgb;
    bloom1 += texture(screenTexture, texcoord + vec2(-2.5,  0.0) * RADIUS_1 * px).rgb;
    bloom1 += texture(screenTexture, texcoord + vec2( 2.5,  0.0) * RADIUS_1 * px).rgb;
    bloom1 *= 0.005;

    vec3 bloom2 = texture(screenTexture, texcoord + vec2( 1.5, -1.5) * RADIUS_2 * px).rgb;
    bloom2 += texture(screenTexture, texcoord + vec2(-1.5, -1.5) * RADIUS_2 * px).rgb;
    bloom2 += texture(screenTexture, texcoord + vec2( 1.5,  1.5) * RADIUS_2 * px).rgb;
    bloom2 += texture(screenTexture, texcoord + vec2(-1.5,  1.5) * RADIUS_2 * px).rgb;
    bloom2 += texture(screenTexture, texcoord + vec2( 0.0, -2.5) * RADIUS_2 * px).rgb;
    bloom2 += texture(screenTexture, texcoord + vec2( 0.0,  2.5) * RADIUS_2 * px).rgb;
    bloom2 += texture(screenTexture, texcoord + vec2(-2.5,  0.0) * RADIUS_2 * px).rgb;
    bloom2 += texture(screenTexture, texcoord + vec2( 2.5,  0.0) * RADIUS_2 * px).rgb;
    bloom2 *= 0.010;

    float dist = RADIUS_2 - RADIUS_1;
    vec3 vivid = (color + (bloom2 - bloom1)) * dist;
    vec3 blend = vivid + color;
    color = pow(abs(blend), vec3(abs(VIVID_POWER))) + vivid;

    fragmentColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}

// Captured screenshots and offscreen passes are always opaque normalized RGB.
void main() {
    upstreamMain();
    fragmentColor = vec4(clamp(fragmentColor.rgb, 0.0, 1.0), 1.0);
}
