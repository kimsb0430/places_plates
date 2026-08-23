# Places & Plates design tokens

`tokens.css` is the source of truth for the public application shell and shared UI styling.

## Token groups

- Primitive colors: `--color-forest-*`, `--color-coral-*`, `--color-sand-*`, `--color-stone-*`
- Semantic colors: `--color-background`, `--color-surface`, `--color-text`, `--color-brand`, `--color-food`, `--color-travel`
- Typography: `--font-*`, `--font-size-*`, `--line-height-*`, `--letter-spacing-*`
- Layout and spacing: `--layout-*`, `--space-*`, `--header-height`
- Shape and elevation: `--radius-*`, `--shadow-*`, `--focus-ring`
- Motion and layers: `--duration-*`, `--ease-*`, `--layer-*`

## Usage rules

1. Shared UI uses semantic tokens instead of primitive colors whenever the meaning is known.
2. Food and destination identity always use `--color-food` and `--color-travel` together with visible text.
3. Page width uses `--layout-max-width` and `--layout-gutter`; sections must not introduce independent desktop widths.
4. Interactive elements keep a visible `:focus-visible` state and at least a 40px default target, increasing to 44px on narrow screens when needed.
5. New motion uses the duration and easing tokens and respects `prefers-reduced-motion`.
6. Photo mock gradients are content fixtures, not interface tokens, and may keep their local palette values.

## Responsive contract

- Minimum supported viewport: 320px
- Required verification widths: 390px and 1440px
- Navigation may collapse below 980px; C06 will provide the complete mobile route navigation.
- At every supported width, `document.documentElement.scrollWidth` must not exceed the viewport width.
