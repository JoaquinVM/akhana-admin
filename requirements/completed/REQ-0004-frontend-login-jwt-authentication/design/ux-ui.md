# Diseño UI/UX - REQ-0004: Pantalla de Login Akhana

---

## 🎨 0. Referencia de Generación en Stitch MCP
- **Proyecto Stitch:** `Akhana Admin POS` (`projects/6337866383860141104`)
- **Pantalla Generada:** `Portal de Acceso - Akhana Admin` (`screens/334bef57add2400497ee59932f0a588e`)
- **Sistema de Diseño Stitch:** `Akhana Admin` (`assets/55b1660e9beb475abf58cb6f4a0e22c7`)
- **Movimiento de Diseño:** *Organic Glassmorphism* biófilo de alta fidelidad, orbes de profundidad atmosférica, bordes finos de 1px en micro-relieve, tipografía *Space Grotesk* + *Plus Jakarta Sans*.

---

## 🎨 1. Identidad Visual y Paleta de Colores

El diseño visual se deriva directamente del **logo de Akhana**:
- El trazo circular zen dorado simboliza energía, calidez, plenitud y movimiento circular.
- La tipografía caligráfica y las hojas botánicas en tonos verdes simbolizan naturaleza, pureza, salud orgánica y crecimiento equilibrado.

```css
:root {
  /* Marca Akhana */
  --akhana-green-primary: #2E5B27;     /* Verde Bosque Profundo (Texto del logo) */
  --akhana-green-dark: #1E3F19;        /* Verde Sombra / Contraste */
  --akhana-green-leaf: #4E873B;        /* Verde Hoja Principal */
  --akhana-green-fresh: #7BB142;       /* Verde Hoja Tierna */
  
  --akhana-gold-zen: #F5B800;          /* Dorado Zen (Círculo pincelado) */
  --akhana-gold-warm: #E6A800;         /* Dorado Cálido / Hover */
  --akhana-gold-amber: #F9A825;        /* Hoja Ámbar */
  --akhana-gold-subtle: rgba(245, 184, 0, 0.12); /* Brillo dorado suave */

  /* Neutrales y Superficies */
  --akhana-bg-gradient: linear-gradient(135deg, #F8FAF7 0%, #EEF4EB 50%, #FAF8F2 100%);
  --akhana-card-bg: rgba(255, 255, 255, 0.94);
  --akhana-card-border: rgba(46, 91, 39, 0.12);
  --akhana-card-shadow: 0 20px 45px -15px rgba(46, 91, 39, 0.14), 0 0 20px rgba(245, 184, 0, 0.08);

  /* Textos y Estados */
  --akhana-text-main: #1C2D19;
  --akhana-text-muted: #5C6E59;
  --akhana-text-label: #2E5B27;
  --akhana-error-bg: #FDEDED;
  --akhana-error-text: #B71C1C;
  --akhana-error-border: #F5C6CB;
}
```

---

## 📐 2. Estructura y Composición de la Pantalla

```text
┌───────────────────────────────────────────────────────────┐
│                     Fondo Suave Orgánico                  │
│                                                           │
│                 ┌──────────────────────┐                  │
│                 │   [ Logo Akhana ]    │                  │
│                 │                      │                  │
│                 │  Panel Administrativo │                 │
│                 │  Bienvenido de nuevo  │                 │
│                 │                      │                  │
│                 │ [ Mensaje de Error ] │  (Condicional)   │
│                 │                      │                  │
│                 │ Usuario              │                  │
│                 │ [👤 admin__________] │                  │
│                 │                      │                  │
│                 │ Contraseña           │                  │
│                 │ [🔒 ••••••••••   👁️] │                  │
│                 │                      │                  │
│                 │ [  Iniciar sesión  ] │                  │
│                 └──────────────────────┘                  │
│                                                           │
│                  © Akhana Admin System                    │
└───────────────────────────────────────────────────────────┘
```

---

## ✨ 3. Microinteracciones y Estados de Interfaz

1. **Logo Central:**
   - Ubicado en la cabecera de la tarjeta (ancho optimizado ~130px) con una sutil animación de entrada (fade-in + ligero slide-down de 8px).
2. **Campos de Entrada (Input Focus):**
   - Transición fluida de borde a `--akhana-green-primary`.
   - Anillo de enfoque suave: `box-shadow: 0 0 0 3px rgba(46, 91, 39, 0.16);`.
   - Botón interactivo para alternar la visibilidad de la contraseña (mostrar/ocultar con icono visual).
3. **Botón de Iniciar Sesión:**
   - Fondo: Degradado de `--akhana-green-primary` con acento dorado en borde inferior o estado hover.
   - Hover: Elevación sutil `transform: translateY(-1px)` y sombra dorada/verde acentuada.
   - Estado de Carga (*Loading*): Deshabilitado, cursor wait, spinner circular estilizado y texto "Iniciando sesión...".
4. **Mensajes de Validación y Alertas:**
   - Errores de campo (requerido): Aparecen debajo del input en tipografía pequeña y color carmesí suave si el campo ha sido tocado (*touched*).
   - Error de autenticación / Servidor: Banner flotante con borde sutil, icono informativo y mensaje claro y seguro.
5. **Responsividad:**
   - Pantallas de escritorio: Tarjeta centrada con ancho máximo de 420px y elevación cuidada.
   - Móviles y tablets (< 480px): Márgenes laterales de 16px, paddings compactos, accesibilidad táctil para botones (mínimo 44px de altura).
