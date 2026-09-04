# Third-party shader sources

The shaders in `app/src/main/assets/shaders/gamenative/opengl/` and
`app/src/main/assets/shaders/gamenative/vulkan/` are adapted from
[GameNative](https://github.com/utkarshdalal/GameNative), revision
`a1459a722e15a3d5df99633a6f7099bd329c1bea`, inspected on 2026-09-04. GameNative
includes Winlator renderer code. The original repository supplies the GNU
General Public License, version 3; its complete text is retained in
`third_party_licenses/GameNative-GPL-3.0.txt`. These shader adaptations retain
that license. The GPL's conditions apply when distributing this derived work;
this notice does not replace the required license or corresponding source.

Per-file upstream paths and SHA-256 hashes are recorded in
`app/src/main/assets/shaders/gamenative/upstream.json`. New integration code is
in `app/src/main/java/com/odin/desktop/shader/gl/GameNativeGlRenderer.kt`.

## AMD FidelityFX Super Resolution

The EASU and RCAS algorithms in `FSR1EasuEffect.frag`, `FSR1RcasEffect.frag`
and the EASU section of `vulkan/window.frag` derive from AMD FidelityFX FSR 1.0.
Copyright (c) 2021 Advanced Micro Devices, Inc. AMD released these algorithms
under the MIT license; the full notice is retained in
`third_party_licenses/AMD-FidelityFX-FSR-MIT.txt`.

Upstream algorithm source:
[GPUOpen FidelityFX-FSR](https://github.com/GPUOpen-Effects/FidelityFX-FSR/blob/master/ffx-fsr/ffx_fsr1.h).

## Adaptations and rendering behavior

- OpenGL shaders use GLES 3.0 syntax, named fragment outputs and an opaque,
  clamped RGB output wrapper. The order remains scaling, color adjustment,
  Toon, FXAA, Vivid, CRT, NTSC. Intermediate passes use two RGBA8 textures.
  As in GameNative's `EffectComposer`, GL effects sample with nearest filtering;
  only the explicit bilinear/fill/stretch scaling pass uses linear filtering.
  The Vulkan family uses linear filtering except for its nearest scaling option.
- FSR on the OpenGL path runs EASU followed by the original RCAS second pass.
  GameNative's Vulkan FSR path instead uses EASU plus its existing lightweight
  contrast sharpen. That difference is retained, including the original comment
  identifying it; Vulkan FSR is not described as full RCAS.
- The Vulkan-family shader is executed as a single GLES pass for screenshot
  preview. Push constants become ordinary uniforms. FXAA still re-reads the
  source and replaces the earlier base effect, and NTSC still samples red and
  blue from the source. It is not a native Vulkan backend or an application hook.
- Screen-effect `resolution`, `TextureSize`, `resW` and `resH` values use output
  screen pixels. Actual source dimensions are retained separately for FSR's
  reconstruction and aspect-ratio calculations. Nearest and bilinear fit,
  fill/crop, stretch and FSR aspect-preserving scaling remain distinct.
- OpenGL CRT retains GameNative's color-channel offset and scanline intensity.
  Its fixed `1024.0` horizontal/vertical phase becomes
  `resolution.x * (1024.0 / 1920.0)` and
  `resolution.y * (1024.0 / 1080.0)`. This preserves the original waveform on
  Odin 3's reference 1920 x 1080 screen and keeps the cadence tied to physical
  pixels at other output sizes. The retained legacy Vulkan CRT base function
  receives the same adaptation. The Vulkan menu's CRT mask keeps its original
  sine coefficients but uses physical `gl_FragCoord.xy` for phase, independently
  of fitted/cropped source UVs. This intentional adaptation keeps its two-pixel
  cadence fixed across image aspect ratios; it does not preserve pixel-for-pixel
  equivalence to upstream at every viewport.
- The GL NTSC phase is derived from the supplied time at a 60 Hz reference
  cadence, modulo its four-frame cycle, so repeated screenshot comparisons can
  select the same phase. GameNative advances that phase once per rendered frame.
- The caller supplies an existing bottom-left-oriented 2D texture and owns all
  frame acquisition, EGL surfaces and pixel readback. No shader here captures
  protected surfaces, injects into another app, or supplies a live game feed.

The public renderer's `VULKAN` and `OPENGL` choices identify the GameNative
effect family. Identical names in the two families can have different equations.
