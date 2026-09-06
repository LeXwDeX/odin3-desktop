#version 300 es
// GameNative/Winlator window.frag adapted to GLES 3 uniforms; single-pass sampling is retained.
// GPL-3.0. The former AMD FSR implementation has been removed.
// See THIRD_PARTY_NOTICES.md for upstream revision and the list of adaptations.
precision highp float;
precision highp int;
uniform vec2 inputResolution;
uniform vec2 outputResolution;
uniform float scaleMode;
uniform sampler2D texSampler;
struct PC {
    float ndcX0;
    float ndcY0;
    float ndcX1;
    float ndcY1;
    int   useTexAlpha;
    int   effectId;
    float sharpness;
    float resW;
    float resH;
    int   effectMask;
    float brightness;
    float contrast;
    float gamma;
    float outW;
    float outH;
};
uniform PC pc;

in vec2 vUV;
layout(location = 0) out vec4 outColor;

const int EFFECT_MASK_TOON  = 1;
const int EFFECT_MASK_FXAA  = 2;
const int EFFECT_MASK_VIVID = 4;
const int EFFECT_MASK_CRT   = 8;
const int EFFECT_MASK_NTSC  = 16;

bool hasEffect(int mask) {
    return (pc.effectMask & mask) != 0;
}

vec3 applyDLS(vec2 uv, float sharp) {
    vec2 texel = 1.0 / max(vec2(pc.resW, pc.resH), vec2(1.0));
    float SAT   = 1.0 + sharp * 0.20;
    float CON   = 1.0 + sharp * 0.12;
    float SHARP = sharp * 1.2;

    vec3 orig = texture(texSampler, uv).rgb;
    vec3 c    = clamp((orig - 0.5) * CON + 0.5, 0.0, 1.0);
    float gray = dot(c, vec3(0.299,0.587,0.114));
    c = mix(vec3(gray), c, SAT);

    vec3 blur = (texture(texSampler, uv + vec2( 0.0,    -texel.y)).rgb
               + texture(texSampler, uv + vec2( 0.0,     texel.y)).rgb
               + texture(texSampler, uv + vec2(-texel.x,  0.0   )).rgb
               + texture(texSampler, uv + vec2( texel.x,  0.0   )).rgb) * 0.25;
    return clamp(c + (orig - blur) * SHARP, 0.0, 1.0);
}

vec3 applyCRT(vec2 uv) {
    float CA = 1.0025;
    vec4 fc = texture(texSampler, uv);
    fc.r = texture(texSampler, (uv-0.5)*CA+0.5).r;
    fc.b = texture(texSampler, (uv-0.5)/CA+0.5).b;
    float sx = abs(sin(uv.x*pc.resW*(1024.0/1920.0))*0.5*0.125);
    float sy = abs(sin(uv.y*pc.resH*(1024.0/1080.0))*0.5*0.375);
    return mix(fc.rgb, vec3(0.0), sx+sy);
}

vec3 applyHDR(vec2 uv) {
    vec2 px = 1.0 / max(vec2(pc.resW, pc.resH), vec2(1.0));
    vec3 c = texture(texSampler, uv).rgb;
    float r1=0.793, r2=0.870;
    vec3 b1=vec3(0.0), b2=vec3(0.0);
    vec2 offs[8] = vec2[8](vec2(1.5,-1.5),vec2(-1.5,-1.5),vec2(1.5,1.5),vec2(-1.5,1.5),
                          vec2(0.0,-2.5),vec2(0.0,2.5),vec2(-2.5,0.0),vec2(2.5,0.0));
    for(int i=0;i<8;i++){
        b1+=texture(texSampler,uv+offs[i]*r1*px).rgb;
        b2+=texture(texSampler,uv+offs[i]*r2*px).rgb;
    }
    b1*=0.005; b2*=0.010;
    float dist=r2-r1;
    vec3 HDR=(c+(b2-b1))*dist;
    return clamp(pow(abs(HDR+c),vec3(1.30))+HDR, 0.0, 1.0);
}

vec3 applyNatural(vec2 uv) {
    mat3 toYIQ = mat3(0.299, 0.596, 0.212,
                      0.587,-0.275,-0.523,
                      0.114,-0.321, 0.311);
    mat3 toRGB = mat3(1.0, 1.0, 1.0,
                      0.95568806,-0.27158179,-1.10817732,
                      0.61985809,-0.64687381, 1.70506455);
    vec3 c = texture(texSampler, uv).rgb;
    vec3 t = c * toYIQ;
    t = vec3(pow(t.r,1.12), t.g*1.2, t.b*1.2);
    return clamp(t * toRGB, 0.0, 1.0);
}

vec3 applyFXAA(vec2 uv) {
    vec2 texel = 1.0 / max(vec2(pc.resW, pc.resH), vec2(1.0));
    vec3 center = texture(texSampler, uv).rgb;
    vec3 north = texture(texSampler, uv + vec2(0.0, -texel.y)).rgb;
    vec3 south = texture(texSampler, uv + vec2(0.0, texel.y)).rgb;
    vec3 west = texture(texSampler, uv + vec2(-texel.x, 0.0)).rgb;
    vec3 east = texture(texSampler, uv + vec2(texel.x, 0.0)).rgb;
    float centerLum = dot(center, vec3(0.299, 0.587, 0.114));
    float edgeLum = max(max(abs(centerLum - dot(north, vec3(0.299, 0.587, 0.114))),
                            abs(centerLum - dot(south, vec3(0.299, 0.587, 0.114)))),
                        max(abs(centerLum - dot(west, vec3(0.299, 0.587, 0.114))),
                            abs(centerLum - dot(east, vec3(0.299, 0.587, 0.114)))));
    vec3 softened = (center * 4.0 + north + south + west + east) * 0.125;
    return mix(center, softened, smoothstep(0.04, 0.20, edgeLum));
}

vec3 applyColorAdjustments(vec3 color) {
    vec3 adjusted = color + vec3(pc.brightness);
    adjusted = (adjusted - 0.5) * (1.0 + pc.contrast) + 0.5;
    adjusted = pow(clamp(adjusted, 0.0, 1.0), vec3(1.0 / max(pc.gamma, 0.01)));
    return clamp(adjusted, 0.0, 1.0);
}

vec3 applyToon(vec3 color) {
    float levels = 6.0;
    return floor(clamp(color, 0.0, 1.0) * levels + 0.5) / levels;
}

vec3 applyVivid(vec3 color) {
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 saturated = mix(vec3(luma), color, 1.25);
    return clamp((saturated - 0.5) * 1.08 + 0.5, 0.0, 1.0);
}

vec3 applyCRTOverlay(vec2 screenPixel, vec3 color) {
    // Odin adaptation: anchor the menu CRT phase to physical output pixels, not fitted source UVs.
    float scanline = 0.86 + 0.14 * sin(screenPixel.y * 3.14159265);
    float grille = 0.94 + 0.06 * sin(screenPixel.x * 3.14159265);
    return clamp(color * scanline * grille, 0.0, 1.0);
}

vec3 applyNTSC(vec2 uv, vec3 color) {
    vec2 texel = 1.0 / max(vec2(pc.resW, pc.resH), vec2(1.0));
    vec3 shifted = color;
    shifted.r = texture(texSampler, uv + vec2(texel.x * 1.25, 0.0)).r;
    shifted.b = texture(texSampler, uv - vec2(texel.x * 1.25, 0.0)).b;
    float bleed = sin((uv.y * max(pc.resH, 1.0) + uv.x * 24.0) * 0.45) * 0.018;
    return clamp(mix(color, shifted + vec3(bleed), 0.65), 0.0, 1.0);
}

void main() {
    vec2 uv = vUV;
    if (scaleMode < 1.5) {
        float scale = scaleMode > 0.5
            ? max(outputResolution.x / inputResolution.x, outputResolution.y / inputResolution.y)
            : min(outputResolution.x / inputResolution.x, outputResolution.y / inputResolution.y);
        vec2 scaledSize = inputResolution * scale;
        vec2 offset = 0.5 * (outputResolution - scaledSize);
        uv = (gl_FragCoord.xy - offset) / scaledSize;
        if (scaleMode < 0.5 && (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)) {
            outColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        uv = clamp(uv, 0.0, 1.0);
    }
    vec4 src = texture(texSampler, uv);
    vec3 rgb;

    if (pc.useTexAlpha != 0 || pc.effectId == 0) rgb = src.rgb;
    else if (pc.effectId == 2) rgb = applyDLS    (uv, pc.sharpness);
    else if (pc.effectId == 3) rgb = applyCRT    (uv);
    else if (pc.effectId == 4) rgb = applyHDR    (uv);
    else if (pc.effectId == 5) rgb = applyNatural(uv);
    else                       rgb = src.rgb;

    if (pc.useTexAlpha == 0) {
        if (hasEffect(EFFECT_MASK_FXAA)) rgb = applyFXAA(uv);
        rgb = applyColorAdjustments(rgb);
        if (hasEffect(EFFECT_MASK_TOON))  rgb = applyToon(rgb);
        if (hasEffect(EFFECT_MASK_VIVID)) rgb = applyVivid(rgb);
        if (hasEffect(EFFECT_MASK_CRT))   rgb = applyCRTOverlay(gl_FragCoord.xy, rgb);
        if (hasEffect(EFFECT_MASK_NTSC))  rgb = applyNTSC(uv, rgb);
    }

    outColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
