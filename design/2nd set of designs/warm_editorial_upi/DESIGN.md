---
name: Warm Editorial UPI
colors:
  surface: '#f9f9f9'
  surface-dim: '#dadada'
  surface-bright: '#f9f9f9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f4'
  surface-container: '#eeeeee'
  surface-container-high: '#e8e8e8'
  surface-container-highest: '#e2e2e2'
  on-surface: '#1a1c1c'
  on-surface-variant: '#524345'
  inverse-surface: '#2f3131'
  inverse-on-surface: '#f0f1f1'
  outline: '#847375'
  outline-variant: '#d7c1c4'
  surface-tint: '#8b4b58'
  primary: '#150004'
  on-primary: '#ffffff'
  primary-container: '#3d0c1a'
  on-primary-container: '#ba7280'
  inverse-primary: '#ffb2bf'
  secondary: '#775746'
  on-secondary: '#ffffff'
  secondary-container: '#ffd4be'
  on-secondary-container: '#7a5a48'
  tertiary: '#040606'
  on-tertiary: '#ffffff'
  tertiary-container: '#1d1f1f'
  on-tertiary-container: '#858687'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffd9de'
  primary-fixed-dim: '#ffb2bf'
  on-primary-fixed: '#390917'
  on-primary-fixed-variant: '#6f3441'
  secondary-fixed: '#ffdbca'
  secondary-fixed-dim: '#e7bea9'
  on-secondary-fixed: '#2c1609'
  on-secondary-fixed-variant: '#5d4030'
  tertiary-fixed: '#e2e2e2'
  tertiary-fixed-dim: '#c6c6c7'
  on-tertiary-fixed: '#1a1c1c'
  on-tertiary-fixed-variant: '#454747'
  background: '#f9f9f9'
  on-background: '#1a1c1c'
  surface-variant: '#e2e2e2'
typography:
  display-lg:
    fontFamily: Bodoni Moda
    fontSize: 40px
    fontWeight: '400'
    lineHeight: 48px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Bodoni Moda
    fontSize: 28px
    fontWeight: '400'
    lineHeight: 36px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Bodoni Moda
    fontSize: 22px
    fontWeight: '500'
    lineHeight: 28px
  headline-sm:
    fontFamily: Bodoni Moda
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: IBM Plex Serif
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: IBM Plex Serif
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: IBM Plex Serif
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Libre Caslon Text
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Libre Caslon Text
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.04em
  label-sm:
    fontFamily: Libre Caslon Text
    fontSize: 10px
    fontWeight: '700'
    lineHeight: 12px
    letterSpacing: 0.08em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1.25rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style

This design system establishes an intimate, quiet, and tactile tone for a peer-to-peer and merchant UPI payment client. It completely rejects generic neo-fintech clichés—dispensing with hyper-saturated blue/green accents, aggressive gamified gradients, and utilitarian spreadsheet densities. Instead, it draws from high-end print publishing, literary journals, and restrained domestic architecture.

Key tenets:
- **Atmospheric Warmth**: Built on natural cream papers and aged ink tones, conveying serenity and deliberate pacing rather than transactional haste.
- **Calm Authority**: Payments are personal exchanges of value. Interfaces prioritize generous breathing room, pristine typographic weight, and unambiguous state transitions.
- **Tactile Restraint**: Interactions feel analog yet fluid. Elevation relies on subtle, soft hairline borders and warm ambient glows rather than synthetic drop shadows.
- **Editorial Distinction**: High-contrast pairing of a literary serif for financial figures and primary headers against an exacting humanist grotesque for metadata and transactional inputs.

## Colors

The palette operates strictly within a curated spectrum of warm organic pigments, dry earthen tones, and deep wine tannins. The environment is fixed to a light, non-glare cream ground.

- **Primary (`#3D0C1A`)**: Deep rich burgundy. Used for authoritative titles, primary button containers, high-value transaction sums, and solid focus anchors.
- **Secondary (`#8C6A58`)**: Soft terracotta umber. Used for secondary metadata, subtle emphasis, currency glyph prefixes, and unselected tab outlines.
- **Tertiary (`#FFFFFF`)**: Pure white highlight. Used for active pressed states, selected badge accents, and interactive contextual highlights.
- **Neutral Canvas (`#FFFFFF`)**: Crisp off-white base. Forms the root page background, mimicking heavy archival book paper.
- **Surface Elevation (`#FFFFFF`)**: Pure card base, applied sparingly over backgrounds for distinct modular grouping.
- **Structural Lines (`#EFE7DE` / `#E8DDD2`)**: Low-contrast borders that frame transaction ledgers and inputs without visually fracturing the screen.
- **Status Accents**: Muted madder red (`#8C2638`) for transaction reversals or payment cancellations; deep laurel olive (`#2B4C3F`) for successful settlement badges. Bright neon success/error fills are strictly forbidden.

## Typography

The typographic hierarchy pairs the literary cadence of Bodoni Moda with the architectural legibility of IBM Plex Serif and Libre Caslon Text.

- **Display & Headlines (`Bodoni Moda`)**: Reserved for large currency sums, contact titles, key account transitions, and screen mastheads. It brings a bespoke, humanized editorial authority to digital numbers, rendering ₹ values with poise.
- **Body & Labels (`IBM Plex Serif` / `Libre Caslon Text`)**: Delivers clear micro-legibility for VPA handles (e.g., `user@upi`), transaction IDs, bank mandates, timestamps, and input hints.
- **Numeric Handling**: Transaction amounts use `display-lg` or `headline-lg` in Bodoni Moda with tabular alignment to ensure clean visual stacking across transaction balance histories.

## Layout & Spacing

Designed for mobile Android viewport profiles, honoring safe areas, standard gestures, and bottom system navigation rails.

- **Grid Model**: Single-column fluid canvas with an intentional `margin` of `1.25rem` (20px) to prevent card crowding near phone bezels.
- **Vertical Flow**: Rhythms rely on expansive white space (`space-xl` for sectional breaks, `space-lg` between distinct transaction clusters). Empty horizontal bands evoke high-end editorial paper layouts rather than endless mobile lists.
- **Touch Targets**: All interactive elements (keypad buttons, pay targets, quick action chips) must respect a minimum touch envelope of 48×48dp, buffered by `space-sm` internal padding.

## Elevation & Depth

This system intentionally excludes heavy, artificial grey shadows and blurry drop spreads. Depth is realized through **tonal stratification and low-contrast borders**:

- **Ground Level (Base)**: `#FFFFFF` canvas.
- **Card & Container Tier**: Pure white (`#FFFFFF`) or tinted paper, outlined with a single, crisp hairline stroke (`1px solid #EFE7DE` or `#E8DDD2`).
- **Warm Ambient Shadow**: Applied solely to floating surfaces (such as bottom transaction sheets or the primary sticky pay CTA). The shadow is softly tinted with raw umber rather than cold black: `0 8px 24px -4px rgba(61, 12, 26, 0.06), 0 2px 6px -1px rgba(61, 12, 26, 0.03)`.
- **Pressed State**: Physical depression is represented by deepening the surface border to `#D8C6B6` and darkening the surface background by 2% without introducing inset shadows.

## Shapes

The geometry strikes a balance between tailored bookish structure and tactile handheld friendliness.

- **Standard Elements (`roundedness: 2`)**: Interactive cards, input fields, contact avatar blocks, and dialogue sheets utilize an 8px (`0.5rem`) base corner radius.
- **Parent Containers & Modals (`rounded-lg`)**: Larger groupings, bottom sheets, and summary billing cards use a 16px (`1rem`) corner radius.
- **Chips & Pills**: Micro-action triggers, status tags, and merchant category chips are fully rounded pill shapes to contrast against rectangular content panes.

## Components

### Buttons
- **Primary CTA**: Deep burgundy (`#3D0C1A`) background, `#FFF8F0` label typography, `0.5rem` border radius, minimum height of 52dp. Active state shifts to tertiary surface (`#FFFFFF`).
- **Secondary / Action**: `#FFFFFF` ground, `1px solid #E8DDD2`, `#3D0C1A` text.
- **Ghost Action**: Transparent background, zero border, `#8C6A58` text with subtle letter-spaced label style.

### Input Fields & Amount Entry
- **UPI ID / VPA Search**: `#FFFFFF` background, border `1px solid #EFE7DE`, rounded-md (`0.5rem`), with placeholder text in `#8C6A58`. On focus, the border shifts to `#3D0C1A` with zero outer ring glow.
- **Amount Entry Pad**: Centered editorial display. The currency glyph (`₹`) is styled in muted `#8C6A58`, while user-entered digits are rendered in `Bodoni Moda` display size (`#3D0C1A`). No underlying form line—only an uncluttered typographic focal point.

### Cards & Ledger Lists
- **Payment Card / Contact Row**: Surfaces sit on `#FFFFFF` canvas, enclosed by `1px solid #EFE7DE`.
- **List Dividers**: Hairline `#EFE7DE` rules indented by `1rem` on the left to align with typography, maintaining continuous rhythm across transaction logs.

### Chips & Badges
- **VPA Filter Chips**: Pill shapes with an unselected fill of transparent canvas and a `1px solid #E8DDD2` border; selected states fill with `#3D0C1A` with `#FFF8F0` text.
- **Payment Status Indicators**: Soft muted badge containers (e.g. laurel olive wash `#EBF2EE` with `#2B4C3F` text for successful transfers) framed with `0.25rem` radius.

### Checkboxes & Radio Controls
- Circular checks and selectors use a delicate `1.5px solid #8C6A58` unselected rim. Checked state fills completely with `#3D0C1A` bearing a crisp `#FFF8F0` checkmark.

### Numerical Keypad
- Bespoke custom in-app pin/amount pad: Clean, borderless touch targets spaced across the bottom third of the mobile screen. Digits rendered in `Bodoni Moda` (`24px`, `#3D0C1A`), with tactile light cream background feedback on tap.