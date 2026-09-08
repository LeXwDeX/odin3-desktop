# UI design system

Odin Desktop uses a compact, left-aligned native interface for a landscape handheld. The verified Odin 3 display is 1920 × 1080 at 369 dpi (about 833 × 468 dp, font scale 1.0). Pixels are not layout units. Compactness comes from a consistent type scale and component insets, while focus remains visible and long translations can grow vertically.

The implementation lives in `OdinDesign.kt`, `OdinPalette.kt`, `SettingsSectionHeader` and `ConsoleModalDialog` / `ConsoleDialogItem`. Material controls receive the same typography, colors and corner scale through `OdinDesktopTheme`.

## Element families and hierarchy

| Family | Roles / levels | Shared design |
| --- | --- | --- |
| Page and navigation | Page, top tabs, hardware dock, full-screen app library | 24 dp horizontal page inset; 64 dp header and dock with matching content reservations; the library removes both reservations |
| Containers | Page surface, settings panel, normal card, dense telemetry card, modal | Insets 24 / 24 / 16 / 12 / 24 dp; neutral surfaces; rounded corners by role |
| Headings | H1 page title, H2 section/dialog title, H3 item title | 22/28 bold, 16/24 semibold, 14/20 medium (size/line height in sp) |
| Reading text | Body, caption, small annotation, input | 12/18, 11/16, 10/14, 14/20 sp; body and heading share the same leading edge |
| Data | Prominent value, card value, metric label, legend, note | 28/34, 20/26, 12/16, 11/14, 10/12 sp; separate compact scale for comparable telemetry |
| Actions | Standard text button, compact text button, arrow/icon action, hardware control | Explicit family insets; action text 12/18 medium; no spaces inserted into strings for alignment |
| Selection and input | Settings option row, modal option row, text input, tab edit controls | Option titles H3; supporting captions; weighted text region; consistent Material input type and shapes |
| Status | Badge, keyboard/controller hint, active mark, warning/error | Caption or button role; short badge insets; color plus label/checkmark/border |
| Application artwork | Home app card, compact library card, modal app icon | Preserve real artwork and aspect ratio; card inset 16 or 12 dp; labels use shared roles and bounded overflow |
| Symbols and charts | Expansion symbol, navigation arrows, telemetry symbols, storage bars and legends | Expansion symbol 48/56 sp; arrows have equal action slots; chart and legend use the same semantic color |
| Overlays | Modal backdrop, modal header/body/footer, AFK black screen and hint | Shared modal geometry and left-aligned footer; AFK remains a separate black-screen mode with its native 14 sp hint |
| Interaction states | Normal, focused, selected, disabled, dangerous | Neutral normal border, accent focus, subdued selected fill, reduced disabled emphasis, danger color for destructive actions |

## Insets are component properties

`OdinSpacing` defines the base scale: 4, 8, 12, 16, 24, 32 dp. It describes relationships between elements: title/description 4, adjacent controls 8 or 12, heading/content 16, panel/page edge 24.

`OdinInsets` defines the inside of each component independently. Equal numbers do not make two component roles interchangeable. Start/end respect text direction.

| Component role | Start | Top | End | Bottom | Text role |
| --- | ---: | ---: | ---: | ---: | --- |
| Standard button | 16 | 12 | 16 | 12 | Button 12/18 |
| Compact button | 16 | 8 | 16 | 8 | Button 12/18 |
| Option row | 16 | 12 | 16 | 12 | H3 14/20, caption below when present |
| Navigation tab | 12 | 8 | 12 | 8 | Body / button |
| Hardware dock control | 8 | 4 | 8 | 4 | Compact label and state within a 48 dp surface |
| Badge / controller hint | 8 | 4 | 8 | 4 | Caption |

All values are dp. A standard one-line custom text button is 42 dp high (12 + 18 + 12); Material buttons retain their minimum interaction size. Arrow actions retain a 44 dp minimum slot. Multi-line text may increase height; do not squeeze a translated label to preserve an arbitrary height. Insets surround the text line box, not the visible ink of individual glyphs. Android font padding is disabled for Compose text to make the line box predictable across roles.

## Surfaces, color and states

Corners are 4 dp for badges, 8 for controls, 12 for cards and 16 for dialogs. Material shapes follow the same scale. Normal borders are 1 dp; focused controls use 2 dp where the component provides a border. Borders render inside existing bounds, so focus does not shift neighboring content.

Use `OdinPalette` for background, surface, card, border, text, dim text, selection, accent, active, warning and danger. Large areas remain subdued. Color retains meaning: focus/navigation accent, active status, performance/charging states and storage categories. Storage free space uses one shared color for internal and removable storage. Hardware light swatches continue to show the actual preset colors.

## Language and layout rules

- Translate system-owned labels only. Preserve stored user category text, including case and legacy category names that older migrations marked as defaults.
- Align a section heading, description and its action group to one leading edge. Within a card, align the card title, description and button to its inner edge.
- Default home uses a vertical title, status, explanation and left-aligned action. A long status cannot steal width from the title or button.
- Allocate independent width to labels, state and actions. Allow descriptions to wrap; use ellipsis only for bounded navigation/app labels whose complete context is available elsewhere.
- Use the same up/down symbols and action widths in every language. Do not shrink text separately for English or Japanese.
- Full-screen application browsing shows the current category and returns with B to the source location.

## Validation boundary

Check actual device screenshots and accessibility bounds at the target density. Check English and Japanese default-home status, left alignment, full language choices, tab-edit actions, app library, dialogs and Dashboard. Preserve stored categories, app membership and preferences during visual checks. A successful build does not prove visual correctness, and the default-size device pass is not exhaustive coverage of every font scale or translation.

The initial device pass covered all six settings sections in English and Japanese, plus the English Dashboard, full-screen library, sort menu and app options. The library retained six columns and three complete rows; the language menu showed all options. Default-home status stayed on one line in both languages, with its action aligned to the card content. Light presets use equal-width columns and centered labels.
