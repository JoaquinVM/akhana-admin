# REQ-0005: Implementación del Menú de Navegación Principal

- **Identificador:** `REQ-0005`
- **Título:** Implementación del Menú de Navegación Principal
- **Complejidad:** `MEDIUM`
- **Estado:** `CLOSED`
- **Fecha de Creación:** 2026-09-25
- **Fecha de Cierre:** 2026-09-25
- **Módulo Principal:** `frontend/`
- **Rama Asociada:** `main`

---

## 🎯 1. Objetivo

Implementar la estructura de navegación principal del sistema mediante un **menú horizontal ubicado en la parte superior de la aplicación**, aprovechando el espacio disponible y manteniendo una interfaz limpia, compacta y profesional.

Las opciones estarán organizadas en grupos funcionales. Al posicionar el cursor sobre un grupo, se desplegarán sus opciones correspondientes. En esta etapa, **únicamente se debe implementar la navegación y la estructura visual del menú**, mostrando el título correspondiente en cada sección para validar la ruta.

---

## 📋 2. Requisitos Funcionales

### 2.1 Estructura y Grupos del Menú
El menú horizontal debe contener los siguientes grupos y opciones:
1. **Ventas:**
   - **POS** (Ruta: `/pos`)
   - **Ventas** (Ruta: `/sales`)
2. **Catálogo:**
   - **Productos** (Ruta: `/products`)
   - **Categorías** (Ruta: `/categories`)
   - **Etiquetas** (Ruta: `/tags`)
3. **Seguridad:**
   - **Usuarios** (Ruta: `/users`)

### 2.2 Ubicación y Comportamiento Visual
- **Disposición:** Menú horizontal en el encabezado superior (*Top Navbar*), integrado armónicamente dentro del layout principal de la aplicación.
- **Espacio:** Ocupa únicamente el espacio vertical necesario sin desperdiciar área de trabajo. No se debe usar sidebar lateral.
- **Interacción Hover:**
  - Al posicionar el cursor sobre un grupo, se despliega suavemente su menú de opciones.
  - Al retirar el cursor del grupo y de sus opciones desplegadas, el menú se cierra de inmediato.
- **Identidad de Marca:** Estilo consistente con la identidad de **Akhana** (paleta verde bosque `#2E5B27`, dorado zen `#F5B800` y acento hoja `#7BB142`).

### 2.3 Estado Activo (Indicador Visual)
- Si el usuario navega a una ruta (ej. `/products`), el grupo padre (**Catálogo**) debe identificarse visualmente como activo en la barra superior.
- Dentro del menú desplegable, la opción activa (**Productos**) debe distinguirse visualmente de las demás opciones.

### 2.4 Ruta Inicial y Redirección
- La sección inicial del sistema es **POS** (`/pos`).
- La ruta raíz `/` debe redirigir automáticamente a `/pos`.
- Si se ingresa a una ruta desconocida estando autenticado, debe redirigir a `/pos`.

### 2.5 Vista Mínima y Título de la Sección
- Al acceder a cualquiera de las 6 rutas, se debe mostrar un contenedor con el título de la sección activa:
  - `/pos` ➔ `POS`
  - `/sales` ➔ `Ventas`
  - `/products` ➔ `Productos`
  - `/categories` ➔ `Categorías`
  - `/tags` ➔ `Etiquetas`
  - `/users` ➔ `Usuarios`
- El título debe reflejar la opción seleccionada (no el grupo).

### 2.6 Estructura Centralizada y Extensible (Data-Driven)
- La definición de los grupos, etiquetas y rutas debe residir en un archivo de configuración centralizado (`navigation.config.ts`), permitiendo agregar o modificar grupos y opciones sin alterar la lógica interna del componente del menú.

### 2.7 Seguridad y Sesión
- Todas las rutas del menú (`/pos`, `/sales`, `/products`, `/categories`, `/tags`, `/users`) deben estar protegidas por `authGuard`.
- La barra de navegación debe incluir el acceso a la información del usuario autenticado y el botón de cerrar sesión (*Logout*), integrando el `AuthService` ya existente.

---

## 🚫 3. Límites Explícitos (Fuera de Scope)
- No implementar lógica de negocio ni CRUDs (usuarios, productos, categorías, etiquetas, ventas, POS).
- No implementar tablas de datos, filtros ni formularios.
- No conectar con APIs adicionales de negocio en el backend.
- No implementar sidebar lateral.

---

## ✅ 4. Cierre Formal del Requerimiento
- **Fecha de Aprobación:** 2026-09-25
- **Aprobado por:** Desarrollador Humano (HITL 3)
- **Resultado de Validación:**
  - Menú de navegación horizontal superior verificado en vivo.
  - Submenús desplegables controlados por estado (solo un menú activo a la vez).
  - Cierre automático del menú al navegar mediante click en cualquier opción.
  - Eliminación completa de la etiqueta "EN LÍNEA".
  - Eliminación completa de los encabezados de módulo en los submenús.
  - Eliminación total de la pantalla Dashboard y redirección a `/pos`.
  - 33/33 pruebas unitarias aprobadas en Vitest.
  - Build de producción verificado sin errores.

