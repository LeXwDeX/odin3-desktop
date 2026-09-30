---
name: generative-ui
description: Create polished interactive HTML prototypes, diagrams, data visualizations, and educational widgets for ODIN_Device, using Antigravity's generative_ui design guidance adapted to Codex. Use for visual UI exploration or rich explanations, not as a replacement for native Android implementation.
---

# Generative UI

Create rich, interactive visual content with clear hierarchy, coherent surfaces,
and compact controls. This is a project-local port of Antigravity's
`generative_ui`, not an installation of its rendering engine.

## Choose the output surface

Respect the user's requested format. Default to a standalone HTML artifact for
large prototypes, dashboards, or simulations. A compact visual that directly
illustrates the conversation can render inline when the host supports it.

- **Standalone artifact:** Write a self-contained `.html` document with local
  CSS and inline JavaScript using the available file-editing tool. Save it in a
  task-owned output location within the authorized workspace. Provide an
  absolute file link, and use `open_in_codex` when available to show the file in
  the side pane. A file tab alone is not evidence that its JavaScript rendered;
  use an available browser preview for runtime verification.
- **Inline in Codex:** When the `visualize` skill is available, read its current
  instructions and follow its fragment, theme, resource, and output contract.
  That host contract takes precedence over the standalone styling guidance
  below. If inline rendering is unavailable, deliver standalone HTML instead.
- **Native Android screen:** Use HTML only as a prototype when that is the
  requested deliverable. Implement production screens in the existing Android
  stack; a browser preview does not establish device behavior or acceptance.

Antigravity's `write_to_file`, `ArtifactMetadata`, and `<agent-embed>` workflow
does not carry over to Codex. Use the actual tools and rendering contract of
the current host rather than emitting those tags or assuming their behavior.

## Visual design and theming

- Aim for a clean, premium aesthetic: deliberate spacing, a clear title/body
  hierarchy, restrained accents, and subtle borders or elevation where they
  clarify surfaces. Keep controls grouped with the content they affect.
- Use semantic surface, text, border, and accent tokens. Pair every surface
  with a readable foreground; secondary text must still contrast against its
  actual background. Follow the requested product theme rather than imposing
  a fixed light or dark palette.
- A standalone document has no injected Antigravity theme or typography.
  Define its own semantic tokens, such as `--ui-background`, `--ui-surface`,
  `--ui-text`, `--ui-muted`, `--ui-border`, and `--ui-accent`, and a system-font
  stack. Supply both appearances with `prefers-color-scheme` or
  `light-dark(...)` plus `color-scheme`, unless a fixed theme was requested.
- For an inline visualization, use only the tokens and typography its host
  documents; leave host-owned theme variables intact. A product mockup should
  use product-specific tokens as required by the host's mockup guidance.
- Prefer self-contained CSS, or the project's existing compiled styling
  assets. The Antigravity-only gstatic Tailwind loader is not a Codex
  dependency. Inline resources must satisfy the current host's allowlist.
- For canvas, resolve the active colors from the actual container's computed
  styles and redraw when the theme changes. Do not assume Antigravity's
  document-level `light` class exists.

## Layout and placement

For standalone prototypes, use a solid page background and opaque product
surfaces. Cards can group related controls and content, using modest rounding,
balanced padding, and subtle elevation. Keep the visual hierarchy purposeful
rather than wrapping every item in another card.

For inline content, keep the surrounding surface transparent and let the
host's layout rules determine framing. Design small: comfortably under roughly
500px tall is the upstream design target, not a Codex viewport limit. Move
content that genuinely needs more room to a standalone artifact unless the
user requests inline placement.

Use content-driven sizing for inline output. Avoid top-level viewport heights
(`100vh`, `h-screen`, `min-h-screen`, or `height: 100%`), which can form a sizing
feedback loop in an auto-sized frame. Use padding for breathing room and reflow
at narrow widths rather than clipping or nesting scroll areas.

## Verification

Check the primary interaction, keyboard access, readable contrast, and layout
at the intended size and a narrow width. Use a browser preview when available
to verify light/dark appearance and runtime behavior. Report any verification
that could not be performed; a generated artifact is not a tested native app.
Device inspection and screenshots remain subject to the project's `AGENTS.md`.

## Upstream reference

The unchanged [Antigravity original](references/antigravity-original.md) is
retained for comparing future upstream changes. Read it only for provenance or
porting work; its host-specific tool names, CSP, theme injection, and embed
limits describe Antigravity, not the Codex execution environment.
