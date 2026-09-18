---
name: Akhana Botanical Precision POS
colors:
  surface: '#FAF8F0'
  surface-dim: '#E9E3D0'
  surface-bright: '#FAF8F0'
  surface-container-lowest: '#FFFFFF'
  surface-container-low: '#F4EFE1'
  surface-container: '#EDE7D4'
  surface-container-high: '#E6DFCB'
  surface-container-highest: '#DED6C1'
  surface-variant: '#E4DEC9'
  on-surface: '#1E2519'
  on-surface-variant: '#4C5544'
  inverse-surface: '#1B2E18'
  inverse-on-surface: '#F5F0D3'
  outline: '#7D8774'
  outline-variant: '#D8D3C1'
  surface-tint: '#4E7A3E'
  primary: '#3B6C2B'
  on-primary: '#FFFFFF'
  primary-container: '#4E7A3E'
  on-primary-container: '#FFFFFF'
  primary-fixed: '#DCECD2'
  primary-fixed-dim: '#B2D49F'
  on-primary-fixed: '#0F240A'
  on-primary-fixed-variant: '#274D1C'
  inverse-primary: '#A3D491'
  secondary: '#5A6F50'
  on-secondary: '#FFFFFF'
  secondary-container: '#E2EBD8'
  on-secondary-container: '#1B2E17'
  secondary-fixed: '#DCE8D4'
  secondary-fixed-dim: '#BDCDB5'
  on-secondary-fixed: '#142410'
  on-secondary-fixed-variant: '#3E5136'
  tertiary: '#7EB53F'
  on-tertiary: '#FFFFFF'
  tertiary-container: '#EBF5DE'
  on-tertiary-container: '#1E3B0B'
  tertiary-fixed: '#8CBF41'
  tertiary-fixed-dim: '#74A836'
  on-tertiary-fixed: '#142707'
  on-tertiary-fixed-variant: '#335B13'
  accent-gold: '#E5A823'
  accent-gold-container: '#FEF6DC'
  accent-gold-on: '#6B4C00'
  background: '#F7F4E9'
  on-background: '#1E2519'
  error: '#C23B2A'
  on-error: '#FFFFFF'
  error-container: '#FDE8E5'
  on-error-container: '#751508'
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

## Brand & Style Identity: Akhana Botanical Organic Modern

The design system is extracted directly from the official **Akhana** brand identity, blending organic elegance (natural botanics, earthy greens, warm honey amber) with high-density enterprise retail checkout ergonomics.

### Core Visual Pillars:
- **Organic Earth Canvas:** Replaces sterile, cold corporate blues with a warm cream/sand canvas (`#F7F4E9`) and crisp surface cards (`#FAF8F0` / `#FFFFFF`). This reduces cashier eye fatigue during long retail shifts.
- **Botanical Green Hierarchy:**
  - **Deep Foliage Green (`#3B6C2B` / `#4E7A3E`):** Serves as primary interactive authority, checkout buttons, and brand accents.
  - **Sprout / Fresh Lime (`#7EB53F` / `#8CBF41`):** Highlights positive status indicators (hardware online, live stock, scale readings, approved QR payments).
  - **Deep Forest Header (`#1B2E18`):** Inverse dark container grounding top and side navigation bars.
- **Amber Gold Prominence (`#E5A823` / `#F59E0B`):** Reflects the third leaf of the Akhana brand motif. Featured prominently across high-priority transaction anchors: the 12 grouped quick-add price tags, item discount input fields, the global ticket discount card, and the `#ORD-8492` order tag pill.

---

## Colors & Semantic Tokens

- **Primary (`#3B6C2B` & `#4E7A3E`):** Akhana Botanical Green. Primary action buttons, active navigation states, and order confirmation triggers.
- **Tertiary / Success Lime (`#7EB53F`):** Live stock confirmations, approved QR/digital transactions, and connected hardware status.
- **Vibrant Amber Gold (`#E5A823` & `#F59E0B`):** Dominant warm commercial accent for quick-add pricing, discount modules, active order badges, and financial highlights.
- **Inverse Forest (`#1B2E18`):** Header and sidebar structural anchor, isolating persistent operational controls.
- **Canvas (`#F7F4E9` / `#FAF8F0`):** Warm organic cream background ensuring high readability without the harsh glare of plain white.
- **Error (`#C23B2A`):** Low-stock warnings, item deletion, void sales, and offline indicators.

---

## Grouped Quick-Add System (12 Top Sellers)

To accelerate register throughput, the terminal provides a dedicated **12-item quick-add panel** grouped into 3 distinct operational categories with vibrant golden amber pricing:
1. **☕ Bebidas & Café (4 items):** Café Espresso ($2.00), Coca-Cola 500ml ($1.80), Agua Mineral 1L ($1.00), Jugo Naranja 500ml ($2.20).
2. **🥖 Panadería & Desayuno (4 items):** Pan Baguette ($0.90), Medialuna Manteca ($0.75), Leche Entera 1L ($1.50), Yogur Granola 250g ($1.20).
3. **🥪 Snacks & Al Paso (4 items):** Sándwich Jamón ($3.50), Papas Rústicas 150g ($2.40), Barra Energética ($1.10), Chocolate Amargo ($1.90).

---

## Shopping Cart: Direct Editable Inputs (Chip-Free Design)

In strict accordance with cashier precision standards, the cart eliminates rapid discount chips to maximize screen clarity and prevent accidental taps. Instead, it provides **dedicated, direct editable input fields**:

### 1. Direct Quantity & Grammage Editing
- **Unit Items:** Stepper buttons (`-` / `+`) flanking a central numeric input (`<input type="number" min="1" max="99">`), allowing direct typing for bulk quantities.
- **Weighed Items:** Dedicated numeric input in grams (`<input type="number" step="25"> g`).

### 2. Direct Discount Inputs (Warm Golden Amber Styling)
- **Line-Item Discount Input:** Clean input field (`Desc: $[ 1.50 ]`) with warm amber border and background highlight.
- **Global Ticket Discount:** Dedicated summary card input (`Descuento Global del Ticket: $[ 2.00 ]`) framed in warm golden amber, with live calculation of total savings (`Total Descuentos: -$3.50`) and authorization ceiling warning (`Máx $20.00`).

---

## Layout Partition (1080p Touch / Desktop Screen)

- **Top Navigation Bar (64px / `#1B2E18`):**
  - Akhana 3-leaf botanical logo + "Admin POS" gold tag.
  - Cashier register monitor (`Caja #01 • Turno Diario • $200.00 base`).
  - Shortcut action triggers (`Confirmar Venta [F9]`, `Cerrar Caja`).
- **Catalog Workspace (65% width):**
  - Barcode scanner input with continuous autofocus laser sweep animation.
  - Category pill filter ribbon (Todos, Bebidas, Abarrotes, Lácteos, Embutidos, Snacks, Limpieza).
  - High-velocity product grid with clear pesable badges, low-stock warnings, and prices.
- **Cart & Checkout Workspace (35% width):**
  - Active ticket header (`#ORD-8492`) with timestamp and item counter.
  - Fast customer selector (`Consumidor Final`).
  - Scrollable interactive cart with inline quantity and discount inputs.
  - Financial breakdown with global discount controls.
  - Multi-tender payment tabs (`Efectivo`, `Pago QR`, `Mixto`), instant change calculation, and thermal receipt auto-print switch.