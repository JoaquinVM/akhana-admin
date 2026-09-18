---
name: Akhana Precision POS
colors:
  surface: '#f8f9fa'
  surface-dim: '#d9dadb'
  surface-bright: '#f8f9fa'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f4f5'
  surface-container: '#edeeef'
  surface-container-high: '#e7e8e9'
  surface-container-highest: '#e1e3e4'
  on-surface: '#191c1d'
  on-surface-variant: '#414754'
  inverse-surface: '#2e3132'
  inverse-on-surface: '#f0f1f2'
  outline: '#727785'
  outline-variant: '#c1c6d6'
  surface-tint: '#005bc0'
  primary: '#005bbf'
  on-primary: '#ffffff'
  primary-container: '#1a73e8'
  on-primary-container: '#ffffff'
  inverse-primary: '#adc7ff'
  secondary: '#545f73'
  on-secondary: '#ffffff'
  secondary-container: '#d5e0f8'
  on-secondary-container: '#586377'
  tertiary: '#006d2c'
  on-tertiary: '#ffffff'
  tertiary-container: '#008939'
  on-tertiary-container: '#ffffff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d8e2ff'
  primary-fixed-dim: '#adc7ff'
  on-primary-fixed: '#001a41'
  on-primary-fixed-variant: '#004493'
  secondary-fixed: '#d8e3fb'
  secondary-fixed-dim: '#bcc7de'
  on-secondary-fixed: '#111c2d'
  on-secondary-fixed-variant: '#3c475a'
  tertiary-fixed: '#89fa9b'
  tertiary-fixed-dim: '#6ddd81'
  on-tertiary-fixed: '#002108'
  on-tertiary-fixed-variant: '#005320'
  background: '#f8f9fa'
  on-background: '#191c1d'
  surface-variant: '#e1e3e4'
typography:
  headline-xl:
    fontFamily: Space Grotesk
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Space Grotesk
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
  body-md:
    fontFamily: Geist
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Geist
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  numeric-hero:
    fontFamily: JetBrains Mono
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.03em
  numeric-lg:
    fontFamily: JetBrains Mono
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  numeric-md:
    fontFamily: JetBrains Mono
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 22px
  label-md:
    fontFamily: Geist
    fontSize: 13px
    fontWeight: '600'
    lineHeight: 18px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1.25rem
  space-xl: 1.75rem
---

## Brand & Style
The design system powers high-volume, enterprise-grade point-of-sale and terminal management where operational speed, visual precision, and error elimination are paramount. Cashiers, floor managers, and retail administrators operate in environments with varied ambient lighting, requiring instant glanceability and rapid touch precision.

The design movement is **Corporate Modern with High-Density Precision**:
- **Utilitarian & Grounded:** Clean structural boundaries, zero extraneous ornamentation, and functional color coding.
- **Dark/Light Hybrid Architecture:** Operational register workspaces (item catalogs, order carts, numpads) use high-clarity light canvas backgrounds to reduce glare under retail fluorescent lighting, while global persistent rails, hardware status monitors, and administrative headers utilize deep dark slate tones to ground focus and isolate critical operational switches.
- **Tactile Feedback:** Crisp borders and rapid 100ms micro-transitions reinforce instant user acknowledgment for barcode scans, touch taps, and price recalculations.

## Colors
The palette balances active transaction clarity with enterprise authority:
- **Primary (`#1A73E8`):** Google Blue powers primary interactive states, active key selection, total calculation triggers, and receipt confirmations.
- **Secondary / Dark Slate (`#1E293B`):** Acts as the high-contrast structural anchor for persistent system rails, register navigation, and critical numeric summaries.
- **Tertiary / Success Emerald (`#34A853`):** Reserved for approved payments, completed transactions, live hardware connectivity (e.g., printers, scanners), and inventory confirmations.
- **Warning Amber (`#FBBC04`):** Flags low receipt paper, pending network sync, or void authorization requests. Paired with dark slate typography for accessible readability.
- **Danger Coral (`#EA4335`):** Exclusively signals transaction voids, item deletions, disconnected payment terminals, and network dropouts.
- **Neutral Light Slate (`#F8F9FA`) & Surface White (`#FFFFFF`):** High-efficiency workspace backgrounds dividing product tiles, cart items, and modifier overlays.
- **Border & Line Neutral (`#E2E8F0`):** Precise dividing rules defining table cells, numeric keypads, and touch targets without visual noise.

## Typography
Typography is tuned for zero-error scanning and high-throughput order entry:
- **Display & Section Headers:** `Space Grotesk` introduces structured, geometric confidence for register station names, category ribbons, and major modal dialogs.
- **Operational UI & Information Flow:** `Geist` provides neutral, high-density legibility across product titles, cashier metadata, and dynamic cart modifiers.
- **Numbers, Quantities & Currency:** `JetBrains Mono` handles all numerical pricing, barcodes, SKU values, tax calculations, and cash pad totals. Tabular figures (`font-variant-numeric: tabular-nums`) ensure strict column alignment across ledger columns and rapid running balances.

## Layout & Spacing
The terminal layout operates on a fixed-ratio structural grid designed for touchscreens (touch POS monitors, iPads, and rugged Android displays) as well as desktop pointer inputs:
- **Terminal Layout Partition (Desktop/Tablet Landscape):**
  - **Left Rail (Fixed 72px):** Dark slate navigation rail for hardware monitor, register switch, returns, and shifts.
  - **Center Workspace (Fluid 60-65%):** Product catalog browser with categorized horizontal pill filters and fluid product grid (3-4 columns).
  - **Right Cart Ledger (Fixed 380px - 440px):** Dedicated order receipt pane, price breakdowns, and massive single-tap checkout action anchors.
- **Mobile/Handheld Breakpoint (< 768px):** Reflows into a segmented swipe view: Catalog View toggles into sliding bottom cart ledger with a persistent subtotal sticky bar.
- **Minimum Touch Target:** All actionable targets (buttons, numpad keys, catalog cards) adhere to a minimum bounding box of 48px x 48px to prevent miss-hits in fast-paced retail environments.

## Elevation & Depth
Depth is created primarily through tonal layering and low-contrast borders rather than deep ambient shadows, ensuring crisp rendering on lower-cost POS LCD displays:
- **Base Surface (`#F8F9FA`):** Lowest canvas level hosting the catalog grid and utility panels.
- **Surface Container (`#FFFFFF`):** Catalog cards, numpads, receipt panes, and inputs with a 1px solid border (`#E2E8F0`).
- **Surface Inset (`#F1F5F9`):** Input search bars, discount code recesses, and disabled item cells.
- **Elevated Modals & Overlays:** Subtle functional shadow (`0 4px 16px -2px rgba(30, 41, 59, 0.12)`) paired with a 1px `#CBD5E1` border for cash drawer prompts, customer lookup sheets, and payment gateway waiting dialogs.
- **Hardware Status Rail (`#1E293B`):** Dark anchor surface using flat planes with no shadows, creating absolute structural separation.

## Shapes
A controlled, compact radius (`0.25rem` base, `0.5rem` for large panels) maintains an industrial, efficient enterprise appearance. 
- Avoid overly rounded pill shapes for primary operational cards to maximize touchable surface area and preserve screen real estate.
- Product item tiles, numpad buttons, and text fields use crisp `0.25rem` corners.
- Checkout checkout drawer surfaces, bottom sheets, and modal confirmation panels use `0.5rem` outer radii.

## Components

### Buttons
- **Primary Tender Button:** `#1A73E8` background, `#FFFFFF` text, `JetBrains Mono` bold total readout, 56px minimum height. Active press feedback uses scale(0.98) with `#1557B0` background.
- **Secondary/Function Keys:** Border `1px solid #CBD5E1`, `#FFFFFF` background, `#1E293B` text (e.g., "Hold Cart", "Split Bill", "Add Note").
- **Destructive/Void Buttons:** Ghost state with `#EA4335` text and light border; solid `#EA4335` fill on confirmation modals.

### Input Fields & Keypads
- **Search & Barcode Scan Input:** Integrated leading scanner icon with constant autofocus capability, `#F1F5F9` background, focusing to `#FFFFFF` with a `2px solid #1A73E8` ring.
- **Numeric Touch Keypad:** Grid layout with large tabular numeric keys (64px height each), `#FFFFFF` fill, `#E2E8F0` borders, providing haptic visual state switches.

### Product Item Cards
- Compact 1:1 or 4:3 ratio touch cards featuring high-contrast title (`Geist` 14px SemiBold), SKU (`JetBrains Mono` 11px), stock counter badge, and prominent price tag in `#1E293B`.
- Out-of-stock items dynamically render with 50% opacity and diagonal warning hash mark.

### Cart Line Items
- Dense, tabular rows with quantity steppers (`-`, value, `+`), title, unit price, and item modifier sub-bullets.
- Swipe-left or quick-tap trigger reveals coral red "Void" action.

### Status Indicators & Chips
- **Hardware Connection Badges:** Emerald green dot (`#34A853`) with pulsing indicator for online barcode scanner, card terminal, and receipt printer.
- **Order Tag Chips:** Compact 24px height, `#E2E8F0` fill, `label-sm` font for table numbers, takeout markers, or customer tier IDs.